# Copilot instructions

## Integration tests

- When adding or adapting an integration test, copy the complete nested test structure of the closest equivalent existing test; do not add only the happy-path/success scenario.
- Preserve relevant nested scenarios for each endpoint or event, including security, validation, and happy-path cases. For synchronization events, cover source-originated skips, already-existing or missing mappings, duplicate mappings, mapping retry/failure, and successful create, update, and delete behavior where applicable.
- Keep scenarios in separate nested inner classes so setup and assertions remain isolated and follow the existing test conventions.
