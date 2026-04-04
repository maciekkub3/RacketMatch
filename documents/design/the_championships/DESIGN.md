# Design System Specification: The Grand Slam Editorial

## 1. Overview & Creative North Star
**Creative North Star: "The Digital Clubhouse"**

This design system transcends the standard "fitness tracker" utility to create a high-end, editorial experience inspired by the prestige of Wimbledon. We are moving away from the "app-as-a-tool" mentality toward "app-as-a-destination." 

The aesthetic is built on **Intentional Asymmetry** and **Tonal Depth**. We eschew rigid, boxy grids in favor of a layout that feels like a premium sports journal. By overlapping serif typography over semi-transparent containers and using a "paper-on-grass" layering technique, we create an interface that feels both heritage-rich and technologically advanced.

---

## 2. Colors & Surface Architecture

### The Color Palette
Our palette is rooted in tradition but executed with modern digital vibrance.
*   **Primary (#004b24):** The "Deep Forest Green." Used for core brand moments and primary actions.
*   **Secondary (#874297):** The "Royal Purple." Used for accentuation, achievement states, and premium callouts.
*   **Neutral/Surface (#f9f9f9):** The "Crisp White." A high-brightness foundation that mimics a freshly painted court line.

### The "No-Line" Rule
**Borders are prohibited for sectioning.** To separate content, you must use background shifts. 
*   Place a `surface-container-low` (#f3f3f3) card on a `surface` (#f9f9f9) background. 
*   The transition of color is the boundary. This creates a "quiet" UI that feels expensive and expansive.

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of materials:
1.  **Base:** `surface` (#f9f9f9) - The "Floor" of the app.
2.  **Sections:** `surface-container-low` (#f3f3f3) - Large structural areas.
3.  **Interactive Cards:** `surface-container-lowest` (#ffffff) - These should appear to "float" or "sit" on top of the lower tiers.

### The "Glass & Gradient" Rule
To add "soul," use subtle linear gradients (Primary to Primary-Container) for hero backgrounds. For floating navigation or modal overlays, apply **Glassmorphism**: 
*   **Fill:** `surface` at 70% opacity.
*   **Effect:** 16px - 24px Backdrop Blur.
*   This mimics the look of a high-end clubhouse window overlooking the courts.

---

## 3. Typography
The typographic pairing is a conversation between heritage and precision.

*   **Display & Headlines (Noto Serif):** Our "Classic Tournament" voice. These should be used with generous leading. For a signature look, use `display-lg` with slight negative letter-spacing (-2%) to create an authoritative, editorial impact.
*   **Body & Labels (Manrope):** Our "Modern Athlete" voice. Manrope provides a clean, technical contrast to the serif headings. Its geometric nature ensures legibility during active use (e.g., tracking a score).

**Editorial Flourish:** Use `label-md` in all-caps with increased letter-spacing (10%) when paired with Purple (`secondary`) for category tags (e.g., "CENTER COURT," "MATCH STATS").

---

## 4. Elevation & Depth

### The Layering Principle
Depth is achieved through **Tonal Layering**. Avoid shadows for standard components. Instead:
*   Place a `primary-container` (#006633) action area directly against a `surface` (#f9f9f9) background. The high contrast provides all the "lift" required.

### Ambient Shadows
If a floating element (like a FAB or a Modal) requires a shadow, it must be an **Ambient Shadow**:
*   **Color:** `on-surface` (#1a1c1c) at 5% opacity.
*   **Blur:** 32px.
*   **Spread:** 0px.
*   This creates a soft, natural glow rather than a harsh digital drop.

### The "Ghost Border" Fallback
If contrast ratios fail or a container feels "lost," use a **Ghost Border**:
*   `outline-variant` (#bfc9bd) at **15% opacity**. 
*   This provides a hint of structure without breaking the "No-Line" rule.

---

## 5. Components

### Buttons
*   **Primary:** `primary` (#004b24) background with `on-primary` (#ffffff) text. Use `md` (0.375rem) roundedness. 
*   **Secondary:** `surface-container-lowest` (#ffffff) with a `secondary` (#874297) Ghost Border.
*   **States:** On hover/tap, transition background to `primary-container` (#006633).

### Cards & Lists
*   **Forbid dividers.** Use `Spacing-6` (2rem) to separate list items.
*   **The "Stat-Block" Card:** Use `surface-container-high` (#e8e8e8) for the background, with `headline-sm` (Noto Serif) for the numerical data.

### Input Fields
*   **Style:** Minimalist underline or soft-tinted box (`surface-container-highest`). 
*   **Focus State:** The label should transition to `secondary` (Purple). Avoid heavy borders; use a 2px bottom-accent in the primary color.

### Custom Racket Components
*   **The Scoreboard Chip:** A `secondary_fixed` (#fdd6ff) container with `on-secondary_fixed` (#340042) text. Use `full` roundedness (9999px) for a pill shape.
*   **The Court Map:** Use `primary` for court surfaces with `outline-variant` (at 40% opacity) for line markings to maintain the premium, subtle feel.

---

## 6. Do’s and Don’ts

### Do:
*   **Use White Space as a Luxury:** Large margins (using `Spacing-12` or `Spacing-16`) signal a premium experience.
*   **Overlap Elements:** Let a serif headline slightly overlap a container edge to break the "boxed-in" feel.
*   **Micro-Textures:** Use a very subtle noise texture (2% opacity) over green backgrounds to mimic the organic feel of grass.

### Don’t:
*   **No "Pure Black":** Use `on-surface` (#1a1c1c) for text. Pure black is too harsh for this palette.
*   **No Default Grids:** Avoid perfectly symmetrical 2x2 grids for dashboards. Try a 60/40 split to create visual interest.
*   **No Heavy Icons:** Icons must be "Light" or "Thin" weight. Never use filled icons unless they are in an "active" state.

### Accessibility Note:
While we use "Ghost Borders" and subtle shifts, always ensure that your primary text (`on-surface`) maintains at least a 4.5:1 contrast ratio against your surface containers. The elegance of the design should never compromise the utility for the athlete.