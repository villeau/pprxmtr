const slack = require('serverless-slack');
const aws = require('aws-sdk');
const sns = new aws.SNS();

const {
  DEBUG,
  ERROR,
  VERIFICATION_TOKEN,
  SLACK_INTEGRATOR_SNS,
  SLACK_INTEGRATOR_SF,
  REGION,
  USER_ID,
  OAUTH_ACCESS_TOKEN,
  LOCALE
} = process.env;

const __ = require('y18n')({ locale: LOCALE }).__;

const debug = (...args) => (DEBUG ? console.log.apply(null, args) : null);
const error = (...args) => (ERROR ? console.error.apply(null, args) : null);

exports.handler = slack.handler.bind(slack);

slack.on('/vince', (msg, bot) => {
  debug(msg);
  let message = {
    text: __('slashCommandResponse', msg.text)
  };
  msg = {...msg, locale: LOCALE, oauth: OAUTH_ACCESS_TOKEN };
  const arn = `arn:aws:sns:${REGION}:${USER_ID}:handle-emoji`;
  exports.callSns(arn, msg, () => bot.replyPrivate(message));
});

exports.callSns = (topic, notification, cb) => {
  debug(`Sending ${JSON.stringify(notification, null, 2)} to topic: ${topic}`);
  const params = {
    Message: JSON.stringify(notification),
    TopicArn: topic
  };
  sns.publish(params, cb);
};
