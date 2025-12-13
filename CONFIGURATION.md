# WXYC Android App Configuration

## Setup Instructions

### Slack Webhook Configuration

The app reads configuration from `app/src/main/assets/config.properties`, which is included in the build but excluded from version control.

1. Copy the template configuration file:
   ```bash
   cp app/src/main/assets/config.properties.template app/src/main/assets/config.properties
   ```

2. Edit `app/src/main/assets/config.properties` and replace `YOUR_SLACK_WEBHOOK_URL_HERE` with your actual Slack webhook URL:
   ```
   slack.webhook.url=https://hooks.slack.com/services/YOUR_WEBHOOK_URL_HERE
   ```

3. The `config.properties` file is added to `.gitignore` to prevent committing sensitive information.

### Security Notes

- The `config.properties` file is included in the app build but excluded from version control
- The template file (`config.properties.template`) is safe to commit
- If you need to share the app with others, provide them with the template and setup instructions
