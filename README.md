
# pprxmtr
pprxmtr is a system that consists of a Slack App and a bunch of AWS Lambda functions that can be used to generate animated GIFs that "approximate" the GIF where a certain professional wrestling promoter falls on his back.

## Requirements
- JDK 8
- Maven 3
- Node
- Serverless
- Slack credentials
- Slack App preferably set up already
  - Slash command
  - Permissions:
    - Add commands (commands)
    - Send messages (chat:write:bot)
    - Access workspace emoji (emoji:read)
    - Access workspace files (files:read)
    - Incoming webhooks (incoming-webhook)
- AWS credentials:
  - [Serverless credentials](https://serverless.com/framework/docs/providers/aws/guide/credentials/)
  - Whatever permissions required to use Lambda, S3, API Gateway, SNS and DynamoDB, see serverless.yml

## Setup
1. [Create a Slack App manually or see link for more instructions](https://github.com/johnagan/serverless-slack-app)
2. Open pom.xml
3. Set missing properties: aws.user.id, s3.bucket.name, slack.oauth.token, slack.verification.token, slack.client.id, slack.client.secret.
4. Change other properties as you see fit, relevant ones are defaultLocale, aws.region and node debug settings. Maven resources plugin will populate serverless.yml files with relevant properties.
5. It might be best to narrow down the permissions given to the Lambda functions in serverless.yml before going to production! **Note** Maven resources plugin generates the actual serverless.yml used, always make your edits to the ones in serverless subfolder. 
6. Once you feel like ready to deploy the app, run
```
mvn clean install
```

if you just want to build everything without running serverless deploy, use
```
mvn clean package
```
7. If everything went well, you should now have two serverless stacks deployed and AWS backend ready to receive slash commands from Slack.
8. Take note of the POST endpoint created for service pprxmtr-request-handler, this will be the request URL that you have to hook your Slack slash command to.


## License
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 1.0 which is available at
http://www.eclipse.org/org/documents/epl-v10.php
