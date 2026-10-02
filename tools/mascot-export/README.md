# Tatsu mascot GLB source

This authoring tool generates the embedded native Filament asset, including all seven VoicePhase animation clips. It is separate from the Android runtime; the app loads only the exported GLB.

Run `npm ci --ignore-scripts` in this directory, then `python3 -m http.server 8800`. Open `http://localhost:8800` in a WebGL browser and click **GLBを書き出す**. Copy the downloaded file to `app/src/main/assets/mascot-native/tatsu.glb`.

`model.js` holds the geometry and materials; `export.js` bakes fiber relief and animations. The offline fallback in `drawable-nodpi/tatsu_mascot_3d.png` is a render of this same model. After changing an asset, run the Android native rendering workflow and inspect its actual AI-screen screenshot. Browser renders alone do not verify Android composition.
