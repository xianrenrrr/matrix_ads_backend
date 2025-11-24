#!/bin/bash

# Load environment variables from parent directory
set -a
source ../matrix_ads_backend_testesttest.env
set +a

# Unset the malformed Firebase JSON - use local file instead
unset GOOGLE_APPLICATION_CREDENTIALS_JSON

# Run the Spring Boot application
./mvnw spring-boot:run
