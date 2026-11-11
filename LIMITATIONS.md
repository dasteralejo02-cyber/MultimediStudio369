# LIMITATIONS - Stage 04.2V

## Bitmap Ownership Model
- Processed Bitmaps are owned by the Composable via `pendingRecycle` list.
- Recycle is deferred to a `LaunchedEffect` after the next composition to avoid use-after-recycle crashes in Compose.

## Export Permission States (API 26-28)
- Runtime request for `WRITE_EXTERNAL_STORAGE` when `maxSdk=28`.
- On permanent denial, launches Settings intent.
- Export is guarded until permission is granted.

## Sample Size Calculation
- Uses long arithmetic + ceil(longest / sample) to avoid overflow and artificial caps.
- Guarantees `inSampleSize` never exceeds 1<<30.
