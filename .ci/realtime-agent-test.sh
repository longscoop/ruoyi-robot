#!/usr/bin/env bash
set -euo pipefail
mvn -B -pl robot-platform-module-ai -am -Dtest=AiModuleSmokeTest test
