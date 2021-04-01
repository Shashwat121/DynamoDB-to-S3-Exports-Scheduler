# DynamoDB-to-S3-Exports-Scheduler

This is a serverless application written in Java 11 to run in the AWS Lambda environment. The Lambda environment supports Java 11 (Corretto) runtime for execution of Java applications. The Lambda function is invoked on a scheduled basis (daily at 12:00 AM IST or 6:30 PM UTC) and carries out it's execution. The application fetches data from a AWS DynamoDB table, processes it, converts it to a readable format (CSV) and then exports it to a AWS S3 bucket for long term storage and retrieval for future
references.

The CSV formatted data once persisted in S3 can be fed to a AWS Kinesis stream or can be crawled upon by AWS Glue for generating insights and carrying out Data Analytics.
