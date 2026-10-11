# Ethos

Minimalistischer Habit Tracker für Android (Kotlin, Jetpack Compose, Room, Material 3).

## Öffnen
1. Android Studio (Ladybug oder neuer) → *Open* → Ordner `Ethos`.
2. Gradle-Sync abwarten (JDK 17). Fehlt der Gradle-Wrapper, schlägt Android Studio die Erzeugung vor (Gradle 8.9).
3. Run auf Gerät/Emulator ab API 26.

## Struktur
- `data/` Room (Entities, DAOs, DB), Repository, DataStore-Settings
- `domain/` reine Logik: `HabitLogic` (BUILD/AVOID, Streaks, Quoten), `ReminderPlanner`
- `reminders/` AlarmManager, Notifications, Boot-/Zeit-Receiver
- `ui/` Compose-Screens je Bereich (Heute, Habits, Statistik, Erinnerungen) + ViewModels

## Material You
Ausgeschaltet. `AppSettings.dynamicColor` (DataStore) steuert es; `EthosTheme` wertet es aus. Es gibt noch keinen Schalter in der UI.
