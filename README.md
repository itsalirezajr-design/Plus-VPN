# Plus VPN — Android prototype

Persian RTL Android prototype for a planned free, one-tap VPN.

## Included
- Android Compose interface branded Plus VPN.
- Basic VLESS and legacy VMess URI parsing.
- TCP endpoint reachability checks and latency sorting.
- GitHub Actions workflow that builds and uploads a debug APK.

## Important
This is **not yet a working VPN**. It does not establish a VPN tunnel and does not include Xray/WireGuard integration, automatic public-node discovery, proxy-handshake verification, or end-to-end traffic verification. The connect control is intentionally disabled to avoid claiming a connection that does not exist.

TCP latency only measures whether a remote TCP port accepts a connection. It is not ICMP ping and does not prove that a proxy or internet tunnel works.

A free app build does not make VPN infrastructure free. Public endpoints can disappear, be overloaded, or be untrustworthy; unlimited free bandwidth and uptime cannot be guaranteed without an operator providing servers.
