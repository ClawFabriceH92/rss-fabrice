# Reprise après le redémarrage d'ARGOS (30/10/2026, 21 h)

Le redémarrage a été lancé pour que `binder_linux` soit rechargé avec ses trois
nœuds (`/etc/modprobe.d/binder.conf` : `devices="binder,hwbinder,vndbinder"`).
Sans eux, Waydroid démarre mais Android ne boote pas (seul `lo`, pas d'adresse
`192.168.240.x`, adb injoignable).

## Contrôles au retour

```bash
ls -l /dev/binder /dev/hwbinder /dev/vndbinder   # les 3 doivent exister
```

Puis la séquence de session Waydroid :

```bash
export XDG_RUNTIME_DIR=/tmp/weston-hermes WAYLAND_DISPLAY=wayland-hermes
mkdir -p $XDG_RUNTIME_DIR/pulse && touch $XDG_RUNTIME_DIR/pulse/native
weston --backend=headless-backend.so --socket=wayland-hermes --idle-time=0 &
systemctl stop dnsmasq                # port 67 : le PXE bloquait Waydroid
dbus-run-session -- waydroid session start &
sleep 60; waydroid status
/opt/android-sdk/platform-tools/adb connect 192.168.240.112:5555
/opt/android-sdk/platform-tools/adb shell getprop sys.boot_completed   # 1
systemctl restart dnsmasq             # remettre le PXE en route après le test
```

## Lancer le test

```bash
cd /root/projects/rss-fabrice
python3 /root/scripts/testeur_apk.py --apk rss-fabrice-v1.6.0.apk \
        --device 192.168.240.112:5555 --parcours tests/parcours-rss.json --attente 12
cat /tmp/testeur-fr.fh.rss-*/rapport.md
```

Un cron one-shot (`1c8d7a6afd54`, « Reprise testeur Waydroid apres reboot ») fait
ces étapes automatiquement 5 minutes après le redémarrage et livre le résultat
sur Telegram.

## Pièges déjà rencontrés

- `pkill -f "waydroid session"` tue le shell appelant : utiliser `[w]aydroid`.
- Sans `$XDG_RUNTIME_DIR/pulse/native`, `lxc-start` avorte sur
  « Failed to setup mount entries » (message final trompeur : « container failed
  to start »).
- Le dnsmasq système (`/etc/dnsmasq.d/pxe.conf`) occupe 0.0.0.0:67.
- Ne jamais cibler le téléphone de Fabrice (Redmi Note 8 Pro branché en USB,
  serial `ea69rs8xtkyt6hqs`) : tests sur Waydroid uniquement.
