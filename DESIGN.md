# Lecturial 2 design

## Direction

The interface is dark, calm, and minimal. Near-black backgrounds dominate, while blue and gold accents are used selectively for weekdays, active navigation, and lesson types. Large shadows, brightly colored cards, and decorative panels are avoided.

## Palette

- Primary background: `#000000`.
- Primary text: `#F4F4F5`.
- Secondary text: `#9A9AA3`.
- Day and selected-tab accent: `primaryFixed` `#B7C7FF`, container `#27407A`.
- Lecture container: `#16506C`.
- Practice container: `#6B4C00`.
- App typeface: Manrope. The widget keeps the system typeface.

The palette does not depend on device dynamic color. Red is reserved for errors and the offline banner. Ocean, Forest, and Sunset provide distinct backgrounds, surfaces, containers, and borders while preserving the same hierarchy and contrast goals.

## Main schedule screen

- The header contains the study group and a compact summary of today’s class count.
- Days appear in a vertical feed, with the current day explicitly identified.
- A next-week divider and date range follows the remaining days of the current week.
- Each lesson shows type and current-state chips, time on the left, and the subject on its own line. The current or nearest class receives a left accent stripe.
- Instructor names are shortened for compact display.
- The default campus service label is hidden because it is not an address.
- A rest hint appears between lessons when the gap is meaningful.
- Bottom navigation contains Schedule, Plans, and Settings, with the selected tab using `primaryContainer`.
- The schedule feed is prepared in advance and avoids stretch overscroll to keep rapid scrolling responsive.
- All states must remain understandable with large system fonts and TalkBack.
- App motion follows `MotionScheme.standard()` without interfering with scrolling.

## Today widget

- Compact 2×2 default size with resizing support.
- No outer stroke; surface color separates the widget from the launcher.
- Material 3 Expressive styling uses a 24 dp outer radius and 16 dp lesson-container radii.
- Android 12+ uses system Material You accent colors; older versions use a Material 3 purple fallback palette.
- Lesson rows do not use colored backgrounds. A thin left stripe distinguishes lecture and practice colors.
- The widget displays a compact date header, lesson and plan counters, the nearest active plan, and a vertically scrollable lesson list.
- The fixed background is `#303030` and primary text is `#C6C6C6`; accent colors are limited to side stripes.
- A row contains the complete time range, subject, and classroom; the campus label is omitted.
- Rows are not individually clickable and have no pressed highlight because vertical gestures are reserved for scrolling.
- Widget options live on a dedicated app screen and include stripe colors, classroom visibility, end-time visibility, title wrapping, text sizes, and spacing.
- Subject names may occupy up to two lines while preserving comfortable scrolling.
- When data is unavailable, the widget asks the user to open the app and refresh the schedule.
- Tapping outside the collection opens Lecturial 2.

## Plans

- Goals, tasks, and deadlines are stored only in the local Room database.
- The Plans tab shows active, due-today, and completed filters plus active/today/overdue counters.
- Every plan can have an optional note and due date, and can be edited, completed, restored, or deleted offline.