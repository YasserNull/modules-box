# Core Rules

## 1. Resources First
- All text must be in `res/values/strings.xml`
- No hardcoded strings in code

## 2. Multi-Language Handling
- If app supports multiple languages, language names are static: `"العربية"` & `"English"`
- Stored exclusively in `values/strings.xml`
- Never translate language names themselves
- If app is single language, no special handling needed

## 3. Contextual Translation
- Translate based on app context & purpose
- For generic names (e.g., "lojyi") → transliterate to `"لوجيي"`
- Don't leave names untranslated when they're just identifiers

## 4. Commit Messages
- Format: `tag: msg`
- No minor changes in messages
- Example: Moving a button is part of "Add button" commit
- Only mention primary changes

## 5. Consistent Renaming
- If changing a button label from `"إزالة"` to `"حذف"`:
  - Update both UI text AND code references
- Applies to all elements, not just buttons

---

## Project Structure

## Legend
- `@` = Required in every project
- `&` = Add only if needed

app/src/
├── @App.kt
├── core/ &
├── data/ &
├── providers/ &
├── receivers/ &
├── services/ &
├── ui/
└── utils/

## Core Directory (app/src/core/)

core/
├── @AppLanguage.kt
├── @AppTheme.kt
├── preferences/
├── managers/
│   └── @LocaleManager.kt
└── &domain/
    ├── &evaluator/
    ├── &trackers/
    └── &executors/

## Data Directory (app/src/data/)

data/
└── &model/

## UI Directory (app/src/ui/)

ui/
├── theme/
│   ├── @Color.kt
│   ├── @Type.kt
│   ├── @Theme.kt
│   ├── &dialogs/
│   ├── &components/
│   └── activities/
└── activities/
    └── $activityName/
        ├── @NameActivity.kt
        ├── segments/
        │   ├── MainFab.kt
        │   ├── MainToolbar.kt
        │   ├── MainActions.kt
        │   ├── MainDrawer.kt
        │   ├── MainContent.kt
        │   └── MainDialogs.kt
        ├── logic/
        └── events/
            ├── MainOnCreate.kt
            ├── MainOnResume.kt
            └── ...

## Directory Notes
- segments/: UI components specific to the activity
- logic/: Business logic for the activity
- events/: Lifecycle and event handlers
- All activity files follow naming: {ActivityName}{Purpose}.kt
- Keep each file focused on single responsibility

---

## Execution Instructions

- Use `git add` only after verifying builds correctly and no execution errors
- Delete temporary Python scripts after use

---

## Additional Guidelines

## 6. Dialogs
- Create separate file in `ui/theme/dialogs/` instead of merging with other files

## 7. Text Corrections
- Fix text in ALL languages unless error exists in only one language's strings

## 8. Animations
- Add animations to transitions and transformations

## 9. Lazy Loading
- Always use lazy loading to prevent screen freezing
- Not all devices are fast

## 10. Code Organization
- Move all code to correct file and folder

## 11. Code Reusability
- Extract repeated code to:
  - Utility functions
  - Shared components
  - Base classes

## 12. Dead Code Cleanup
- Remove dead code after modifications that don't change behavior
- If you make a change and it has no effect, revert or remove it
- Keep codebase clean and minimal

## 13. Logging
- Add logs in sensitive areas:
  - Critical operations
  - Error handling
  - State transitions
  - User interactions that affect data
- Use appropriate log levels (debug, info, warning, error)

## 14. Comments for Non-Obvious Decisions
- Add comments when I point out a problem
- After discussing and solving an issue, add comment explaining:
  - Why this approach was chosen
  - What alternatives were considered
  - Why this solution is better
- Example: "Using CoroutineScope(Dispatchers.IO) instead of GlobalScope to prevent memory leaks and allow proper lifecycle management"