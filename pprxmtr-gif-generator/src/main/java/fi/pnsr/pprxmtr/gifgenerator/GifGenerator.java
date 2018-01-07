package fi.pnsr.pprxmtr.gifgenerator;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imgscalr.Scalr;

import fi.pnsr.pprxmtr.gifgenerator.AnimatedGIFWriter.GIFFrame;

public class GifGenerator {

	private static final int FRAME_DELAY_IN_MS = 33;

	private static final Logger LOG = LogManager.getLogger();

	private static final int NUMBER_OF_ANIMATED_FRAMES = 14;

	private static final int TARGET_SIZE_IN_PIXELS = 96;

	public static byte[] generateGif(byte[] original) {

		ByteArrayOutputStream os = new ByteArrayOutputStream();

		try {
			InputStream is = new ByteArrayInputStream(original);
			BufferedImage image = ImageIO.read(is);

			LOG.info("Original image read successfully, starting to create gif.");

			int originalWidth = image.getWidth();
			int originalHeight = image.getHeight();

			LOG.debug("Original image dimensions: {}x{}", originalWidth, originalHeight);

			int targetWidth = Math.min(originalWidth, TARGET_SIZE_IN_PIXELS);
			int targetHeight = Math.min(originalHeight, TARGET_SIZE_IN_PIXELS);

			if (originalWidth > originalHeight) {
				double downscaleRatio = (double) targetWidth / (double) originalWidth;
				targetHeight = (int) Precision.round(downscaleRatio * originalHeight, 0);
				LOG.debug("New target height is {}", targetHeight);
			} else if (originalHeight > originalWidth) {
				double downscaleRatio = (double) targetHeight / (double) originalHeight;
				targetWidth = (int) Precision.round(downscaleRatio * originalWidth, 0);
				LOG.debug("New target width is {}", targetWidth);
			}

			LOG.debug("Target image dimensions: {}x{}.", targetWidth, targetHeight);
			image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight,
					(BufferedImageOp) null);

			originalWidth = image.getWidth();
			originalHeight = image.getHeight();

			// Write the first frame, where the "face" is stationary for a while.
			AnimatedGIFWriter writer = new AnimatedGIFWriter(true);
			writer.prepareForWrite(os, -1, -1);
			writer.writeFrame(os, image, 1200);

			// Transformation variables

			// Negative X shear creates a parallelogram that leans to the right =>
			// . _________
			//  /        / . = origin after shear
			// /________/
			//
			// Shear does not move the actual origin, so some compensation is added to
			// translation transform to get some sort of approximation where the "actual"
			// origin is.
			double shearX = -0.05;
			double shearY = 0.00;
			int shearCompensation = (int) Precision.round(targetWidth / 24, 0);

			// Initial scaling values. Y axis scale value is decremented after every frame
			// in order to create a better approximation of falling gradually.
			double scaleX = 0.95;
			double scaleY = 0.95;
			double yScaleDecrement = 0.03;

			for (int i = 0; i < NUMBER_OF_ANIMATED_FRAMES; ++i) {

				int newX = (int) (image.getWidth() * scaleX);
				int newY = (int) (image.getHeight() * scaleY);

				BufferedImage processedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = processedImage.createGraphics();

				// Three transformations are applied to the new frame:
				// 1. Origin is moved so that the bottom right corner after scaling matches the original
				// bottom right corner (before shear and "sliding out of image" compensation).
				// 2. Then, the image is scaled to the new dimensions calculated above.
				// 3. Finally, negative X shear is applied (explained above).
				g.translate(originalWidth - newX + shearCompensation, originalHeight - newY + 0.5);
				g.scale(scaleX, scaleY);
				g.shear(shearX, shearY);

				// Bicubic interpolation results in better image quality in downscaled images.
				// Dithering is handled by the gif writer.
				Map<RenderingHints.Key, Object> renderingHints = new HashMap<>();
				renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				renderingHints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
				renderingHints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);

				g.setRenderingHints(renderingHints);
				g.drawRenderedImage(image, null);

				// Set the newly created frame as basis for the next one.
				image = processedImage;
				scaleY -= yScaleDecrement;

				// Write the new frame to the gif. GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND is
				// needed to make sure that the next frame starts from a blank slate.
				// Last empty frame is displayed longer to make the gif feel more "natural".
				GIFFrame frame = null;
				if (i == NUMBER_OF_ANIMATED_FRAMES - 1) {
					frame = new GIFFrame(processedImage, 200, GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND);
				} else {
					frame = new GIFFrame(processedImage, FRAME_DELAY_IN_MS, GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND);
				}
				writer.writeFrame(os, frame);
			}

			writer.finishWrite(os);
			os.close();

		} catch (Exception e) {
			LOG.error("Exception occured when generating gif.", e);
			return ArrayUtils.EMPTY_BYTE_ARRAY;
		}

		LOG.info("GIF created, returning byte array.");
		return os.toByteArray();
	}
}
