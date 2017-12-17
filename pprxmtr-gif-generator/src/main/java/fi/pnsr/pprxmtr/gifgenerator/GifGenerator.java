package fi.pnsr.pprxmtr.gifgenerator;

import java.awt.Color;
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

	private static final Logger LOG = LogManager.getLogger();

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

			double aspectRatio = (double) originalWidth / (double) originalHeight;

			LOG.debug("Aspect ratio: {}", aspectRatio);

			int targetWidth = originalWidth > TARGET_SIZE_IN_PIXELS ? TARGET_SIZE_IN_PIXELS : originalWidth;
			int targetHeight = originalHeight > TARGET_SIZE_IN_PIXELS ? TARGET_SIZE_IN_PIXELS : originalHeight;
			if (originalWidth > originalHeight) {
				targetHeight = (int) Precision.round(targetHeight / aspectRatio, 0);
				LOG.debug("New target height is {}", targetHeight);
			} else if (originalHeight > originalWidth) {
				targetWidth = (int) Precision.round(targetWidth * aspectRatio, 0);
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
			int shearCompensation = 4;

			// Initial scaling values. Y axis scale value is decremented after every frame
			// in order to create a better approximation of falling gradually.
			double scaleX = 0.95;
			double scaleY = 0.95;

			int numberOfAnimatedFrames = 15;
			for (int i = 0; i < numberOfAnimatedFrames; ++i) {

				int newX = (int) (image.getWidth() * scaleX);
				int newY = (int) (image.getHeight() * scaleY);

				BufferedImage processedImage = new BufferedImage(image.getWidth(), image.getHeight(),
						BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = processedImage.createGraphics();

				// Set up the background for transparency.
				g.setColor(Color.MAGENTA);
				g.drawRect(0, 0, targetWidth, targetHeight);

				// Three transformations are applied to the new frame:
				// 1. Origin is moved so that the bottom right corner after scaling matches the original
				// bottom right corner (before shear and "sliding out of image" compensation).
				// 2. Then, the image is scaled to the new dimensions calculated above.
				// 3. Finally, negative X shear is applied (explained above).
				g.translate(originalWidth - newX + shearCompensation, originalHeight - newY + 0.5);
				g.scale(scaleX, scaleY);
				g.shear(shearX, shearY);

				// Nearest neighbor has slightly worse image quality than other interpolation
				// methods but less likely to result artifacts in transparency calculations due
				// to smaller chance of pure magenta getting changed into some blended color.
				// Dithering is handled by the gif writer.
				Map<RenderingHints.Key, Object> renderingHints = new HashMap<>();
				renderingHints.put(RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				renderingHints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
				renderingHints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);

				g.setRenderingHints(renderingHints);
				g.drawRenderedImage(image, null);

				// Set the newly created frame as basis for the next one.
				image = processedImage;
				scaleY -= 0.04;

				// Write the new frame to the gif. GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND is
				// needed to make sure that the next frame starts from a blank slate.
				GIFFrame frame = new GIFFrame(processedImage, 40, GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND,
						Color.MAGENTA.getRGB());
				writer.writeFrame(os, frame);
			}

			// Final empty frame.
			BufferedImage processedImage = new BufferedImage(image.getWidth(), image.getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = processedImage.createGraphics();
			g.setColor(Color.MAGENTA);
			g.fillRect(0, 0, targetWidth, targetHeight);
			g.drawRenderedImage(image, null);

			GIFFrame frame = new GIFFrame(processedImage, 40, GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND,
					Color.MAGENTA.getRGB());
			writer.writeFrame(os, frame);
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
