# ZERO Project Engineering Rules (AGENTS.md)

## Mandatory 4-Language Localization Rule (EN / ZH / JA / KO)
ZERO ships with complete, native 4-language UI support:
- English (`app/src/main/res/values/strings.xml`) — default fallback
- Simplified Chinese (`app/src/main/res/values-zh/strings.xml`)
- Japanese (`app/src/main/res/values-ja/strings.xml`)
- Korean (`app/src/main/res/values-ko/strings.xml`)

Whenever adding or modifying ANY feature, screen, dialog, snackbar/capsule, widget, or formatter:
1. **NEVER hardcode user-facing copy** in Kotlin/Compose files or ViewModels. Always declare string keys in `strings.xml` and read them via `stringResource(R.string.*)` or `context.getString(R.string.*)` (or model typed UI state enums/sealed classes rendered in Compose).
2. **ALWAYS add translations simultaneously to ALL 4 resource files** (`values`, `values-zh`, `values-ja`, `values-ko`) in the same commit/turn, preserving exact format placeholders (`%1$s`, `%1$d`, etc.).
3. If adding new date/time or recurrence text formatters in pure Kotlin (`core/common`), support all 4 locales (`en`, `zh`, `ja`, `ko`) via an explicit `locale: Locale` parameter.
4. **ALWAYS verify** by running:
   ```bash
   JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
   ```
   `LocalizationCompletenessTest` automatically fails the build if any key or format placeholder is missing or mismatched in any of the 4 languages.
