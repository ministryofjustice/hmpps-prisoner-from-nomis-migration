#!/bin/bash

cd src/test/kotlin

# Find all packages
all_packages=($(find uk/gov/justice/digital/hmpps/prisonerfromnomismigration -mindepth 1 -maxdepth 1 -type d -exec basename {} \;))

# Get all packages that require a dedicated executor
dedicated_executor_packages=()
for i in $1; do dedicated_executor_packages+=($i); done

# Validate parameter test-executors-count (which needs to be a parameter because it's used in the "parallelism" declaration)
required_executors="${#dedicated_executor_packages[@]}"
if [[ $CIRCLE_NODE_TOTAL -ne $((required_executors+1)) ]]; then
  echo "The 'test-executors-count' must be 1 more than the number of packages in parameter 'test-executor-packages".
  exit 1
fi

# Get all other packages that don't require a dedicated executor - these will be run on a single executor
common_executor_packages=()
for package in "${all_packages[@]}"; do
  if [[ ! " ${dedicated_executor_packages[@]} " =~ " ${package} " ]]; then
    common_executor_packages+=("$package")
  fi
done

# Function to find test classes for a package and write them to file
find_test_classes() {
  local package=$1
  local output_file=$2

  if [ -d "uk/gov/justice/digital/hmpps/prisonerfromnomismigration/$package" ]; then
    local test_files=$(find "uk/gov/justice/digital/hmpps/prisonerfromnomismigration/$package" -name "*.kt" \
      | xargs -r grep -l -E '@Test|@ParameterizedTest' 2>/dev/null || true)
    echo "Found test_files for package=$package: $test_files"

    # Convert test files to class names
    if [ ! -z "$test_files" ]; then
      echo "$test_files" \
        | sed 's@/@.@g' \
        | sed 's/\.kt//g' \
        >> "$output_file"
    fi
  else
    echo "Directory uk/gov/justice/digital/hmpps/prisonerfromnomismigration/$package does not exist. Check parameter test-executor-packages."
    exit 1
  fi
}

index_output_file="test_classes$CIRCLE_NODE_INDEX.txt"
> "$index_output_file"

if [[ "$CIRCLE_NODE_INDEX" -lt "$((CIRCLE_NODE_TOTAL-1))" ]]; then
  # Create a file containing the classes for this dedicated executor package
  echo "Finding test classes for dedicated executor from package=${dedicated_executor_packages[$CIRCLE_NODE_INDEX]}"
  find_test_classes "${dedicated_executor_packages[$CIRCLE_NODE_INDEX]}" "$index_output_file"
else
  # Create a single file to contain test classes bundled from all other packages
  echo "Finding test classes for common executor from packages=${common_executor_packages[@]}"
  for common_executor_package in "${common_executor_packages[@]}"; do
    find_test_classes "$common_executor_package" "$index_output_file"
  done
fi

echo "Executor $CIRCLE_NODE_INDEX will run tests for classes: $(cat $index_output_file)"

# Fail the build if no test files were found - unless we're in the bundled executor (which could potentially have zero tests)
if [[ "$CIRCLE_NODE_INDEX" -lt "$((CIRCLE_NODE_TOTAL-1))" ]]; then
  [ -s test_classes$CIRCLE_NODE_INDEX.txt ] || circleci-agent step halt
fi
