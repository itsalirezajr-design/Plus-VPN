# Plus VPN

Persian RTL Android VPN client with one-tap automatic server selection.

## Implemented in this milestone
- Android `VpnService` TUN interface.
- Official XTLS libXray Android core downloaded by CI from a pinned upstream release.
- Automatic VLESS node retrieval from a public community feed.
- Candidate failover and HTTPS verification through the device VPN tunnel.
- Foreground notification with a disconnect action.

## Infrastructure and privacy limits
Plus VPN does not operate or own VPN gateway servers. It discovers publicly shared third-party nodes. Those operators may log or inspect traffic metadata; nodes can disappear or be blocked. No uptime, privacy, bandwidth, or speed guarantee is possible. Do not use public volunteer nodes for banking, passwords, or sensitive accounts. A genuinely Plus VPN-owned server fleet requires hosting infrastructure and an operator; an APK alone cannot create remote VPN servers.

The public feed is an external dependency and can change or become unavailable. Automatic selection checks the device's HTTPS path, not only TCP port reachability.

## Build
GitHub Actions downloads the pinned libXray Android AAR and builds the debug APK.
