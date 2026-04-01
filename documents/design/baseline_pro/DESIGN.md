# Design System Strategy: Pro-Circuit Editorial

## 1. Overview & Creative North Star
**Creative North Star: "The Kinetic Arena"**
This design system moves beyond the "utility app" aesthetic to create a high-performance digital stadium. The objective is to capture the explosive energy of a match point through **Kinetic Asymmetry** and **Tonal Depth**. We reject the static, "boxed-in" layout of traditional sports apps in favor of a layered, editorial approach that feels as fast as a 120mph serve. 

By overlapping bold, athletic typography with translucent glass layers and high-contrast "Electric Lime" accents, we create a sense of forward motion. The interface doesn't just display data; it vibrates with the intensity of the court.

---

## 2. Colors: The High-Contrast Court
Our palette is rooted in the "Deep Charcoal" of high-end sports gear and the "Electric Lime" of the ball in flight.

*   **The Primary Engine:** Use `primary` (#d1fc00) and `primary_container` (#d1fc00) sparingly. These are high-frequency accents meant to draw the eye to Critical Actions (CTAs) and win states.
*   **The "No-Line" Rule:** Visual separation must be achieved through background shifts, not strokes. Use `surface_container_low` (#111415) sections against a `background` (#0c0e0f) to define content areas. Never use 1px solid borders to divide information.
*   **Surface Hierarchy & Nesting:** Treat the UI as physical layers. 
    *   Base: `surface` (#0c0e0f)
    *   Secondary Areas: `surface_container_low` (#111415)
    *   Active Cards: `surface_container_high` (#1d2021)
*   **The "Glass & Gradient" Rule:** For floating navigation or score overlays, use a 60% opacity of `surface_container` with a 20px backdrop blur. Apply a subtle linear gradient from `primary` to `primary_container` on hero buttons to create a "glowing" effect that feels tactile and premium.

---

## 3. Typography: Athletic Authority
We pair the geometric aggression of **Lexend** with the precision of **Plus Jakarta Sans**.

*   **Display & Headlines (Lexend):** Use `display-lg` and `headline-lg` for scores and "Win" states. These should be set with tight letter-spacing (-2%) to mimic the bold, condensed type found on professional jerseys.
*   **Title & Body (Plus Jakarta Sans):** Use `title-md` for player names and `body-md` for match stats. This font provides the high legibility required for fast-moving users in a competitive environment.
*   **Tactical Labels:** `label-sm` should always be uppercase with increased letter-spacing (+5%) when used for category tags (e.g., "LIVE," "MATCH POINT"), utilizing the `tertiary` (#ffeb9c) token to denote technical data.

---

## 4. Elevation & Depth: Tonal Layering
Traditional drop shadows are too "software-standard." We use ambient light and tonal shifts.

*   **The Layering Principle:** Elevate a player’s profile card by placing a `surface_container_highest` (#232628) card on top of a `surface_container_low` (#111415) background. The contrast in charcoal depth creates natural lift.
*   **Ambient Shadows:** For floating action buttons, use a shadow with a 32px blur, 10% opacity, using a tinted version of `primary` to suggest the glow of the accent color hitting the dark surface.
*   **The "Ghost Border" Fallback:** If containment is required for accessibility, use `outline_variant` (#464849) at **15% opacity**. It should be felt, not seen.
*   **Kinetic Glass:** Use semi-transparent layers for top navigation bars so the vibrant colors of the "court" (content) bleed through as the user scrolls, maintaining a sense of place.

---

## 5. Components: Precision Gear

### Buttons: The Power Stroke
*   **Primary:** Background `primary_container` (#d1fc00), text `on_primary_container` (#4c5d00). Use `xl` (1.5rem) roundedness. These should feel like "pills"—smooth and aerodynamic.
*   **Tertiary:** No background. Use `primary` text with an underline that only appears on hover.

### Cards: The Match Summary
*   **Constraint:** Forbid divider lines.
*   **Structure:** Use a `surface_container_high` card. Separate the "Set Scores" from "Player Names" using a 1.5rem (`6`) vertical gap from the spacing scale.
*   **Interactive State:** On press, the card should scale to 98% and shift background to `surface_bright` (#292d2e).

### Chips: Filter & Status
*   **Live Status:** Use a `primary` background with a subtle pulse animation. 
*   **Filter Chips:** Use `secondary_container` (#444749). When selected, transition to `primary_container`.

### Input Fields: The Score Entry
*   **Style:** Minimalist. Only a bottom "Ghost Border" (15% opacity `outline`). Use `headline-sm` for the actual numerical input to make the data feel important.

### Signature Component: The "Match Momentum" Sparkline
*   A custom line chart using the `primary` color with a `primary_container` outer glow, placed behind player stats to show performance trends without adding visual clutter.

---

## 6. Do’s and Don’ts

### Do:
*   **Do** use asymmetrical margins (e.g., `8` on the left, `12` on the right) for header elements to create a sense of speed.
*   **Do** use `primary` sparingly—it’s the "electricity" in the system; too much of it will fatigue the user's eyes.
*   **Do** overlap elements. Let a player’s "Action Photo" break the container of a card to create 3D depth.

### Don’t:
*   **Don’t** use pure black (#000000) for large surfaces; it kills the depth of the "Deep Charcoal" palette. Use `surface`.
*   **Don’t** use standard 1px dividers. If you need to separate content, use a `1.5` (0.375rem) spacing gap or a tonal shift.
*   **Don’t** use sharp corners. Everything in this system should feel "ergonomic"—stick to the `md` to `xl` roundedness scale.