#!/usr/bin/env python3
"""Regression tests for the protected noVNC reverse-proxy URL mapping."""
from __future__ import annotations

import argparse
import asyncio
import tempfile
import unittest
from pathlib import Path

from aiohttp import web
from aiohttp.test_utils import TestClient, TestServer

from remote_console import create_app


class RootAssetProxyTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        upstream_app = web.Application()

        async def echo_path(request: web.Request) -> web.Response:
            return web.Response(text=request.path)

        upstream_app.router.add_route("*", "/{tail:.*}", echo_path)
        self.upstream = TestServer(upstream_app)
        await self.upstream.start_server()

        self.temp_dir = tempfile.TemporaryDirectory()
        args = argparse.Namespace(
            finish_file=str(Path(self.temp_dir.name) / "finished"),
            finish_token="test-finish-token",
            novnc_port=self.upstream.port,
        )
        self.console = TestClient(TestServer(create_app(args)))
        await self.console.start_server()

    async def asyncTearDown(self) -> None:
        await self.console.close()
        await self.upstream.close()
        self.temp_dir.cleanup()

    async def test_root_routes_preserve_full_upstream_path(self) -> None:
        paths = (
            "/vnc.html",
            "/app/ui.js",
            "/app/styles/base.css",
            "/core/rfb.js",
            "/vendor/pako/lib/deflate.js",
            "/images/icons/clipboard.svg",
            "/include/util/polyfill.js",
            "/defaults.json",
            "/mandatory.json",
            "/websockify",
        )
        for path in paths:
            with self.subTest(path=path):
                response = await self.console.get(path)
                self.assertEqual(response.status, 200, path)
                self.assertEqual(await response.text(), path, path)

    async def test_legacy_novnc_route_remains_compatible(self) -> None:
        response = await self.console.get("/novnc/vnc.html")
        self.assertEqual(response.status, 200)
        self.assertEqual(await response.text(), "/vnc.html")


if __name__ == "__main__":
    unittest.main(verbosity=2)
