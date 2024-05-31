#!/usr/bin/env bash

#
# Provides functions for splitting tests into multiple executors. Used by the `tests` job in `config.yml`.
#

function find_all_packages {
  ROOT_PATH=$1

  echo "About to ls on root path $ROOT_PATH"
  directories=$(ls -p "$ROOT_PATH" | grep '/' | tr -d '/')
  printf "%s\n" "${directories[@]}"
}

function validate_number_of_executors {
  NUMBER_OF_EXECUTORS=$1
  NUMBER_DEDICATED_EXECUTOR_PACKAGES=$2

  if [[ $NUMBER_OF_EXECUTORS -ne $((NUMBER_DEDICATED_EXECUTOR_PACKAGES+1)) ]]; then
    echo "The 'test-executors-count' must be 1 more than the number of packages in parameter 'test-executor-packages".
    exit 1
  fi
}

function find_test_classes() {
  ROOT_PATH=$1
  PACKAGE=$2
  OUTPUT_FILE=$3

  if [ -d "$ROOT_PATH/$PACKAGE" ]; then
    # shellcheck disable=SC2038
    test_files=$(find "$ROOT_PATH/$PACKAGE" -type f -name "*.kt" \
      | xargs -r grep -l -E '@Test|@ParameterizedTest' 2>/dev/null || true)

    # Convert test files to class names
    if [ -n "$test_files" ]; then
      echo "$test_files" \
        | sed 's@/@.@g' \
        | sed 's/\.kt//g' \
        >> "$OUTPUT_FILE"
    fi
  else
    echo "Directory $ROOT_PATH/$PACKAGE does not exist. Check parameter test-executor-packages."
    exit 1
  fi
}

last_executor() {
  EXECUTOR_NUMBER=$1
  TOTAL_EXECUTORS=$2

  if [[ "$EXECUTOR_NUMBER" -eq "$((TOTAL_EXECUTORS-1))" ]]; then
    return 0
  else
    return 1
  fi
}
