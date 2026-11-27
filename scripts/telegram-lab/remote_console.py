#!/usr/bin/env python3
"""Email-gated noVNC reverse proxy with an explicit emulator-session save button."""
from __future__ import annotations

import argparse
import asyncio
import html
from pathlib import Path

from aiohttp import ClientSession, WSMsgType, web


def html_page(token: str) -> str:
    safe_token = html.escape(token, quote=True)
    return """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Telegram CI Session Provisioning</title>
<style>
body{margin:0;background:#101820;color:#e9f0f4;font-family:Arial,sans-serif}
header{padding:14px 18px;background:#172630;border-bottom:1px solid #36505f}
h1{font-size:18px;margin:0 0 8px}p{font-size:13px;line-height:1.6;margin:5px 0}
.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;padding:12px 18px}
button{border:0;border-radius:6px;padding:12px 18px;background:#16875b;color:white;font-weight:bold}
button:disabled{opacity:.5}.warning{color:#ffd27d}.screen{height:calc(100vh - 165px);min-height:420px}
iframe{width:100%;height:100%;border:0;background:#05080b}
</style></head>
<body>
<header>
<h1>مختبر جلسة Telegram — Android Emulator</h1>
<p>هذه شاشة محاكي Android داخل GitHub Actions. افتح OSINT Workbench ثم أكمل تسجيل الدخول في Telegram Web بحساب اختبار مستقل.</p>
<p class="warning">لا تستخدم حسابك الشخصي الأساسي. الموافقة على QR تنشئ جلسة جهاز جديدة. لا تشارك رابط هذه الصفحة أو صورة QR مع أي شخص.</p>
<p>بعد ظهور محادثات Telegram والتأكد من أن الجلسة تعمل، اضغط «حفظ الجلسة وإنهاء».</p>
</header>
<div class="toolbar">
<button id="finish">حفظ الجلسة وإنهاء</button>
<span id="status">المحاكي جاهز. أكمل تسجيل الدخول أولاً.</span>
</div>
<div class="screen"><iframe id="vnc" allow="clipboard-read; clipboard-write"></iframe></div>
<script>
const url=new URL('/novnc/vnc.html',location.href);
url.searchParams.set('host',location.hostname);
url.searchParams.set('port',location.port || (location.protocol==='https:'?'443':'80'));
url.searchParams.set('encrypt',location.protocol==='https:'?'true':'false');
url.searchParams.set('path','novnc/websockify');
url.searchParams.set('autoconnect','true');
url.searchParams.set('resize','remote');
document.getElementById('vnc').src=url.toString();
document.getElementById('finish').addEventListener('click',async()=>{
 const button=document.getElementById('finish');
 button.disabled=true;
 document.getElementById('status').textContent='جارٍ إيقاف المحاكي وحفظ صورة الجلسة المشفرة...';
 try{
  const response=await fetch('/api/finish',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:'TOKEN_PLACEHOLDER'})});
  if(!response.ok) throw new Error('finish request rejected');
  document.getElementById('status').textContent='تم إرسال طلب الحفظ. يمكنك إغلاق هذه الصفحة.';
 }catch(e){
  button.disabled=false;
  document.getElementById('status').textContent='تعذر إرسال طلب الحفظ. حاول مرة أخرى.';
 }
});
</script>
</body></html>""".replace("TOKEN_PLACEHOLDER", safe_token)


def create_app(args: argparse.Namespace) -> web.Application:
    app = web.Application()
    finished = Path(args.finish_file)
    token = args.finish_token

    async def home(request: web.Request) -> web.Response:
        return web.Response(text=html_page(token), content_type="text/html")

    async def finish(request: web.Request) -> web.Response:
        try:
            body = await request.json()
        except Exception:
            return web.Response(status=400, text="bad request")
        if body.get("token") != token:
            return web.Response(status=403, text="forbidden")
        finished.write_text("approved", encoding="utf-8")
        return web.json_response({"ok": True})

    async def status(request: web.Request) -> web.Response:
        return web.json_response({"finished": finished.exists()})

    async def proxy(request: web.Request) -> web.StreamResponse:
        tail = request.match_info.get("tail", "")
        # The public console mounts noVNC under /novnc, while websockify serves it at /.
        # Strip that external prefix for both static assets and the WebSocket endpoint.
        upstream_tail = tail.removeprefix("novnc/")
        upstream_url = "http://127.0.0.1:" + str(args.novnc_port) + "/" + upstream_tail
        is_websocket = request.headers.get("Upgrade", "").lower() == "websocket"

        if is_websocket:
            client_ws = web.WebSocketResponse()
            await client_ws.prepare(request)
            protocols = request.headers.getall("Sec-WebSocket-Protocol", [])
            async with ClientSession() as session:
                upstream_ws = await session.ws_connect(
                    upstream_url,
                    params=request.query,
                    protocols=protocols,
                )

                async def client_to_upstream() -> None:
                    async for message in client_ws:
                        if message.type == WSMsgType.TEXT:
                            await upstream_ws.send_str(message.data)
                        elif message.type == WSMsgType.BINARY:
                            await upstream_ws.send_bytes(message.data)
                        elif message.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.ERROR):
                            break

                async def upstream_to_client() -> None:
                    async for message in upstream_ws:
                        if message.type == WSMsgType.TEXT:
                            await client_ws.send_str(message.data)
                        elif message.type == WSMsgType.BINARY:
                            await client_ws.send_bytes(message.data)
                        elif message.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.ERROR):
                            break

                tasks = [
                    asyncio.create_task(client_to_upstream()),
                    asyncio.create_task(upstream_to_client()),
                ]
                _, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                for task in pending:
                    task.cancel()
                await upstream_ws.close()
                await client_ws.close()
            return client_ws

        headers = {
            key: value
            for key, value in request.headers.items()
            if key.lower() not in ("host", "connection", "upgrade", "content-length")
        }
        async with ClientSession() as session:
            async with session.request(
                request.method,
                upstream_url,
                params=request.query,
                data=await request.read(),
                headers=headers,
            ) as upstream:
                body = await upstream.read()
                response_headers = {
                    key: value
                    for key, value in upstream.headers.items()
                    if key.lower()
                    not in (
                        "content-length",
                        "transfer-encoding",
                        "connection",
                        "content-encoding",
                        "content-security-policy",
                    )
                }
                return web.Response(
                    body=body,
                    status=upstream.status,
                    headers=response_headers,
                )

    app.router.add_get("/", home)
    app.router.add_post("/api/finish", finish)
    app.router.add_get("/api/status", status)
    app.router.add_route("*", "/novnc/{tail:.*}", proxy)
    return app


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--novnc-port", type=int, default=6081)
    parser.add_argument("--finish-file", required=True)
    parser.add_argument("--finish-token", required=True)
    args = parser.parse_args()
    web.run_app(create_app(args), host=args.host, port=args.port, access_log=None)


if __name__ == "__main__":
    main()
