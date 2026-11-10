# Reading secrets from a secret store (free)

By default the app reads `LLM_API_KEY`, `EMBEDDING_API_KEY` and `MONGODB_URI` from environment variables
(on Render: the dashboard, stored encrypted, never in git). If you prefer the app to pull them from a
dedicated secret store at startup, use **AWS Systems Manager Parameter Store**. Standard-tier parameters
(including `SecureString`, encrypted with the AWS-managed `aws/ssm` key) have no per-parameter charge.
Check AWS's current pricing page before relying on that. Do **not** use AWS Secrets Manager here: it is billed
per secret per month.

## 1. Create the parameters (type `SecureString`, tier `Standard`)

The part after the prefix becomes the Spring property name, with `/` turned into `.`:

| Parameter name | Becomes property |
|---|---|
| `/incident-copilot/prod/copilot/llm/api-key` | `copilot.llm.api-key` |
| `/incident-copilot/prod/copilot/embedding/api-key` | `copilot.embedding.api-key` |
| `/incident-copilot/prod/spring/data/mongodb/uri` | `spring.data.mongodb.uri` |

```bash
aws ssm put-parameter --type SecureString --tier Standard --name /incident-copilot/prod/copilot/llm/api-key --value 'YOUR_GROQ_KEY'
aws ssm put-parameter --type SecureString --tier Standard --name /incident-copilot/prod/copilot/embedding/api-key --value 'YOUR_GEMINI_KEY'
aws ssm put-parameter --type SecureString --tier Standard --name /incident-copilot/prod/spring/data/mongodb/uri --value 'mongodb+srv://...'
```

## 2. Create a read-only IAM user for the app

Policy (replace region and account id). Add `kms:Decrypt` only if you encrypt with your own KMS key.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"],
    "Resource": "arn:aws:ssm:ap-south-1:123456789012:parameter/incident-copilot/prod*"
  }]
}
```

Create an access key for that user. It can read only those parameters.

## 3. Build and run with the AWS profile

```bash
mvn -Paws package
SPRING_PROFILES_ACTIVE=aws-ssm AWS_REGION=ap-south-1 \
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... STORE_TYPE=mongo \
java -jar target/app.jar
```

If a parameter cannot be read, startup fails on purpose (no silent fall back to offline mode).

## 4. On Render

1. In `Dockerfile` change `ARG MAVEN_ARGS=""` to `ARG MAVEN_ARGS="-Paws"`.
2. In the Render dashboard set only these env vars: `SPRING_PROFILES_ACTIVE=aws-ssm`, `AWS_REGION`,
   `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `STORE_TYPE=mongo`. Remove `MONGODB_URI`, `LLM_API_KEY` and
   `EMBEDDING_API_KEY`.

## Honest trade-off

The app still needs one bootstrap credential (the AWS access key) outside the secret store, and on Render that
key is itself an environment variable. What you gain: the real API keys and DB password live in one audited
place, can be rotated without redeploying, and the AWS key can only read three parameters. If the app ran on
AWS (EC2, ECS, App Runner) it would use an IAM role and need no stored key at all.

## Other free options

- **Google Cloud Secret Manager**: free tier of 6 active secret versions and 10,000 access operations per month
  (per Google's pricing page). Needs a Google Cloud project, which may require a billing account. It is not wired
  into this project yet.
- **Render environment groups / secret files**: no code change, free, and the simplest option if you do not need
  an external store.
