# Finger Spark Design Direction

## Design Brief

Refine Finger Spark into a polished, child-friendly Android app with clear hierarchy,
distinct Paint and Game entry points, accessible controls, restrained motion, and
layouts that hold up with Bangla, long names, and larger text. Preserve every
existing feature and fully offline operation.

## Identity: Creative Studio

The visual direction is a small creative studio: a quiet canvas, deliberate ink
typography, and two illustrated choices. Paint uses a green brush motif; Game
uses a coral target motif. These illustrations represent the activities and are
drawn locally with Android Canvas.

This is a custom native Views design informed by Material principles. It does
not add a Material component framework, a web wrapper, or a network dependency.

## Foundations

| Role | Value |
| --- | --- |
| Background | `#F8FAF9` |
| Primary text and camera toolbar | `#1F2C31` |
| Secondary text | `#536669` |
| Paint surface | `#DCF2E7` |
| Paint accent and primary action | `#1C7058` |
| Game surface | `#FFE5DB` |
| Game accent | `#B14633` |
| Dividers | `#DBE4DF` |
| Primary heading | 28 sp, medium |
| Mode title | 22 sp, medium |
| Mode detail | 14 sp |
| Surface radius | 8 dp |
| Normal side inset | 24 dp |
| Maximum content width | 560 dp |
| Maximum camera toolbar width | 352 dp |

Typography uses the system font with a medium weight. Labels wrap where needed.
Greeting text can ellipsize after two lines; the full saved name remains intact.

## Layout

- The language switch remains a circle at top right, with the wordmark at top left.
- The hand and orbit sit in a bounded artwork region above the welcome content.
- Greeting, mode choices, and creator information stay in the lower content flow.
- Mode choices use two equal-width, equal-height columns when space permits.
- Content below 288 dp wide, or a font scale above 1.25, switches to stacked mode rows.
- Short windows reduce the artwork region from 176 to 140 dp.
- Larger windows center the content within a 560 dp column.
- Content remains scrollable. The keyboard inset and fixed header have reserved space.
- Creator information has a divider and small circular portrait rather than another card.
- Camera controls use a compact dark tray with capture as the primary accent.
- Camera controls are hidden until the camera screen is pressed.

## Interaction And Accessibility

- Each mode tile is one accessible button with a localized name and detail.
- Mode illustrations and the orbit are decorative and do not create extra focus targets.
- The name field has inline validation and supports the keyboard Done action.
- The language switch preserves Bn/En casing and has a localized target description.
- Buttons use visible ripple feedback and icon tooltips.
- Screen-reader touch exploration prevents premature toolbar auto-hide.
- On supported Android versions, toolbar timeout respects the accessibility timeout.
- Disabled system animations or battery saver produce a static home illustration
  and skip the animated splash.
- No decorative background animation runs continuously.

## Implementation

`SparkStyle.kt` holds the palette and ripple styling. `ModeTileView.kt` owns the
mode-choice presentation and static Canvas art. `HomeLayoutPolicy.kt` centralizes
responsive layout decisions and has six focused JVM tests.

The UI update is version 1.1.0. Device screenshots and live touch verification
are still pending because no authorized phone or configured emulator was
available during implementation.

## References

- [Google: Expressive Design research](https://design.google/library/expressive-material-design-google-research)
- [Material Design: Color roles](https://m3.material.io/styles/color/the-color-system)
- [NN/g: Designing for Kids, Cognitive Considerations](https://www.nngroup.com/articles/kids-cognition/)
