# Wand aim: crosshair convergence

The Water Wand now points at the crosshair. One shared helper, `AimUtil`
(`com.wildbound.skills`, common source set), is the single source of truth for
the look direction, the wand-tip locator and every effect derived from them.

## AimUtil

- `lookDir(player, tickDelta)` - normalized, interpolated view vector. It is
  exactly the direction the camera renders with, so it is the crosshair
  direction. `lookDir(player)` samples the freshest rotation at the firing
  moment and is what the activation/charge payloads send to the server.
- `right(look)` / `up(look)` - orthonormal screen basis (same convention as the
  server `side()` / `up()` helpers).
- `wandTip(eye, look)` - the crystal-tip locator. The tip sits on the crosshair
  ray, `TIP_DEPTH` (0.96 blocks) ahead of the eye. `wandTip(player, tickDelta)`
  is the interpolated variant used by client presentations.

Because the tip is on the aim ray by construction, no hardcoded side/up spawn
offsets remain: idle droplets, charge strands, the server charge core, the
lance formation origin and the Aqua FX anchor all use `wandTip`.

## First person

`WandAimItemPoseMixin` injects into the first-person item submission
(`ItemStackRenderState$LayerRenderState.submit`, right after the display
transform). It reads the live pose matrix for the current frame, locates the
grip (pommel, model y=0) and the crystal apex (model y=22.1), and rotates the
wand about the grip until the tip lands on the view-space -Z axis (the
crosshair). The solve is redone every frame against the interpolated camera
matrix, so fast pitch changes never detach the tip from the centre. The
rotation is blended in/out and a subtle procedural sway is layered on top.

`WandAimHandMixin` scopes the correction to the exact submission of our wand
(flag raised at HEAD, cleared at every RETURN of `submitArmWithItem`), so other
items, GUI icons and third-person hand layers are never affected.

## Third person

`WandArmPoseMixin` flags `AvatarRenderState` when the wand is in the main hand
(duck interface `WandAimRenderState`). `WandArmModelMixin` then poses the main
arm bow-style at the tail of `HumanoidModel.setupAnim`:

    arm.xRot = head.xRot - PI/2 + swayPitch
    arm.yRot = head.yRot + swayYaw

The arm tracks the head pitch/yaw exactly; the off-hand keeps its vanilla pose.
The held wand follows the arm, so it points where the player looks.

## Sway, not a decoupled camera

The old idle camera breathing is gone. `WandAimState` provides a procedural
sway of at most ~1.6 degrees (yaw) / ~1.3 degrees (pitch), damped to 35% while
charging. It is applied to the wand pose and the arm pose - never to the
camera. While aiming (wand + a radial skill ready), `CameraShakeMixin` applies
no additive rotation at all: no cinematic clips, no impact shake. The
crosshair, the wand tip and `AimUtil.lookDir` always agree.

## Server side

`SkillAimContext` still carries the client aim vector into the executor.
`AquaSkillExecutors` starts the Water Bolt charge core, the lance formation,
the Aqua Whip and the Pressure Lance at `AimUtil.wandTip(eye, look)`; the
projectile march and hit detection run along `look`, i.e. straight down the
crosshair ray. `AquaSkillFxService` anchors the eye-origin FX at the same
locator, and the Water Bolt geometry now grows out of the tip (local z ~ 0.3)
instead of starting two blocks ahead.

## Tests

`AimUtilTest` checks basis orthonormality (including straight up/down), that
the tip lies on the aim ray at `TIP_DEPTH`, and the degenerate-look fallback.
Pure math, no Minecraft runtime.
