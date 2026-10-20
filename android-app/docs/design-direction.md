# Finger Spark Design Direction

## Implementation Brief

Design Finger Spark as a children's creative play app with crisp white surfaces,
charcoal text, mint Paint and coral Game tiles, generous touch targets, and
responsive spacing. Keep the hand animation above the welcome content, the
circular BN/EN control at top right, and creator details at the bottom. Use motion
to show interaction and feedback.

## Chosen Style

Playful flat design informed by Material 3 Expressive principles. This is a custom
native Views theme; it does not claim to implement the Material 3 component library.

Solid surfaces and distinct mode colors make the two activities easy to scan.
Typography carries hierarchy, while icon and text labels work together. Motion is
reserved for the existing hand, orbit, splash, and interaction feedback.

## Foundations

| Role | Value |
| --- | --- |
| Background | `#F9FBFA` |
| Primary text | `#1C2426` |
| Secondary text | `#516061` |
| Paint | `#B7EFD6` |
| Game | `#FFC3B8` |
| Mode surface corners | 8 dp |
| Page side spacing | 24 dp |
| Mode minimum height | 88 dp |
| Language target | 54 x 54 dp |

`SparkStyle.kt` owns the primary palette and ripple surface construction.
The camera keeps its live visual feedback; the home screen provides a quiet
starting point before the child enters either activity.

## Layout Rules

- The artwork and welcome content share a scrolling layout, avoiding fixed-screen
  artwork coordinates that can collide with text on smaller phones.
- The scroll viewport reserves space for the fixed brand and language controls.
- Mode descriptions and creator copy wrap instead of forcing fixed-height text.
- The camera toolbar divides the available width between its five actions.
- The static home background does not schedule continuous redraws.
- Home animation is paused when the activity stops.

## Research

- [Google: Expressive Design research](https://design.google/library/expressive-material-design-google-research)
- [Material Design: Color roles](https://m3.material.io/styles/color/the-color-system)
- [NN/g: Designing for Kids, Cognitive Considerations](https://www.nngroup.com/articles/kids-cognition/)

The style choice is a design judgment informed by these sources. It still needs
observation with children and visual checks on a connected phone.
