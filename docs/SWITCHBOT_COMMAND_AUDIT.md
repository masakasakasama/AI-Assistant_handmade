# SwitchBot command audit — 2026-10-03

All 84 official device documents were read. The app only exposes operations with a documented command and parameter. Source: https://github.com/OpenWonderLabs/SwitchBotAPI/tree/main/devices

| Family | Start / on | Stop / off | Parameter / handling |
|---|---|---|---|
| K10+, K10+ Pro, S1, S1 Plus | start | stop | default |
| S10/S20 (Robot Vacuum Cleaner S10/S20) | startClean | pause | sweep, fanLevel 1, waterLevel 1, times 1; pause default |
| K10+ Pro Combo, K11+, K20+ Pro (including Robot Vacuum Cleaner K20 Plus Pro) | startClean | pause | sweep, fanLevel 1, times 1; pause default |
| Blind Tilt | fullyOpen | closeDown | default |
| Bot switchMode | turnOn | turnOff | default |
| Bot pressMode/customizeMode/unknown mode | press | unsupported | default; button labelled 押す, voice confirmation required |
| Infrared appliances except Others | turnOn | turnOff | default; AC setAll validates range |
| Infrared Others | unsupported | unsupported | custom button name and commandType customize required; generic power hidden |
| Relay Switch 2PM | unsupported | unsupported | channel 1/2 required; no channel is guessed |
| Hubs, sensors, locks, cameras, unknown types | unsupported | unsupported | no generic power is sent |

Documented native standard-power devices:

- `air purifier pm2.5`: [air-purifier-pm25](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/air-purifier-pm25.md)
- `air purifier table pm2.5`: [air-purifier-table-pm25](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/air-purifier-table-pm25.md)
- `air purifier table voc`: [air-purifier-table-voc](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/air-purifier-table-voc.md)
- `air purifier voc`: [air-purifier-voc](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/air-purifier-voc.md)
- `battery circulator fan`: [battery-circulator-fan](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/battery-circulator-fan.md)
- `battery circulator fan 2 pro`: [battery-circulator-fan-2-pro](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/battery-circulator-fan-2-pro.md)
- `candle warmer lamp`: [candle-warmer-lamp](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/candle-warmer-lamp.md)
- `ceiling light`: [ceiling-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/ceiling-light.md)
- `ceiling light pro`: [ceiling-light-pro](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/ceiling-light-pro.md)
- `circulator fan`: [circulator-fan](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/circulator-fan.md)
- `color bulb`: [color-bulb](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/color-bulb.md)
- `curtain`: [curtain](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/curtains-blinds/curtain.md)
- `curtain 3`: [curtain-3](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/curtains-blinds/curtain-3.md)
- `floor lamp`: [floor-lamp](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/floor-lamp.md)
- `garage door opener`: [garage-door-opener](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/garage-door-opener.md)
- `humidifier`: [humidifier](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/humidifier.md)
- `humidifier2`: [evaporative-humidifier](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/evaporative-humidifier.md)
- `humidifier2`: [evaporative-humidifier-auto-refill](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/evaporative-humidifier-auto-refill.md)
- `permanent outdoor lights`: [permanent-outdoor-lights](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/permanent-outdoor-lights.md)
- `plug`: [plug](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/plug.md)
- `plug mini (eu)`: [plug-mini-eu](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/plug-mini-eu.md)
- `plug mini (jp)`: [plug-mini-jp](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/plug-mini-jp.md)
- `plug mini (us)`: [plug-mini-us](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/plug-mini-us.md)
- `relay switch 1`: [relay-switch-1](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/relay-switch-1.md)
- `relay switch 1pm`: [relay-switch-1pm](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/plugs-switches/relay-switch-1pm.md)
- `rgbic neon rope light`: [rgbic-neon-rope-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/rgbic-neon-rope-light.md)
- `rgbic neon wire rope light`: [rgbic-neon-wire-rope-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/rgbic-neon-wire-rope-light.md)
- `rgbicww ceiling light`: [rgbicww-ceiling-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/rgbicww-ceiling-light.md)
- `rgbicww floor lamp`: [rgbicww-floor-lamp](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/rgbicww-floor-lamp.md)
- `rgbicww strip light`: [rgbicww-strip-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/rgbicww-strip-light.md)
- `smart radiator thermostat`: [smart-radiator-thermostat](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/smart-radiator-thermostat.md)
- `standing fan`: [standing-circulator-fan](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/climate-control/standing-circulator-fan.md)
- `strip light`: [strip-light](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/strip-light.md)
- `strip light 3`: [strip-light-3](https://github.com/OpenWonderLabs/SwitchBotAPI/blob/main/devices/lighting/strip-light-3.md)

Manual UI and voice adapters use the same profiles. Unrecognized types and unavailable operations fail before HTTP. Tests verify commands, parameters, unsupported devices, and voice blocking. Documentation checks do not prove physical operation; no household commands were issued during verification.

The startClean parameter is serialized as a JSON object (String/Object schema), not as an escaped string. Bot mode is read from device status during sync. Curtain3, K11+ and K20 Plus Pro list-type aliases are explicitly handled even where command-table type names differ.
