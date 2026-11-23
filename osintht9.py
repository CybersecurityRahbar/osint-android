#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ALKASER ULTIMATE - Professional OSINT Tool
Version 13.3 - Ultimate Social & Web Search with Rich Media Report
Author: Enhanced Edition
"""

import sys
import os
user_local = os.path.expanduser('~/.local/lib/python3.13/site-packages')
if user_local not in sys.path:
    sys.path.insert(0, user_local)

import requests
import time
import threading
import re
import json
import hashlib
import subprocess
import random
import tkinter as tk
from tkinter import messagebox, scrolledtext, filedialog, ttk, Menu
from bs4 import BeautifulSoup
from urllib.parse import quote_plus, urlparse, urljoin, parse_qs, unquote
from datetime import datetime
import logging
from io import BytesIO
from concurrent.futures import ThreadPoolExecutor, as_completed
import webbrowser
import socket
import dns.resolver
import whois
from typing import List, Dict, Any, Optional, Tuple
import csv
import base64
import traceback

# ===================== SerpAPI Integration =====================
SERPAPI_KEY = ""
SERPAPI_AVAILABLE = False

user_site_packages = os.path.expanduser('~/.local/lib/python3.13/site-packages')
if os.path.exists(user_site_packages) and user_site_packages not in sys.path:
    sys.path.insert(0, user_site_packages)
    print(f"[*] Added user site-packages to path: {user_site_packages}")

try:
    from serpapi import GoogleSearch, BingSearch, YoutubeSearch
    SERPAPI_AVAILABLE = True
    print("[+] SerpAPI library loaded successfully.")
except ImportError as e:
    print(f"[!] SerpAPI not installed. Install: pip install google-search-results --break-system-packages")
    SERPAPI_AVAILABLE = False

# المكتبات الاختيارية
LIBRARIES_STATUS = {}
try:
    from PIL import Image, ImageTk
    PIL_AVAILABLE = True
    LIBRARIES_STATUS['PIL'] = True
except ImportError:
    PIL_AVAILABLE = False
    LIBRARIES_STATUS['PIL'] = False

try:
    import colorama
    from colorama import Fore, Style
    colorama.init()
    COLORAMA_AVAILABLE = True
    LIBRARIES_STATUS['colorama'] = True
except ImportError:
    COLORAMA_AVAILABLE = False
    LIBRARIES_STATUS['colorama'] = False

try:
    from instagrapi import Client
    from instagrapi.exceptions import LoginRequired, ClientError, ClientLoginRequired, ChallengeRequired
    INSTAGRAPI_AVAILABLE = True
    LIBRARIES_STATUS['instagrapi'] = True
except ImportError:
    INSTAGRAPI_AVAILABLE = False
    LIBRARIES_STATUS['instagrapi'] = False

try:
    import pytesseract
    PYTESSERACT_AVAILABLE = True
    LIBRARIES_STATUS['pytesseract'] = True
except ImportError:
    PYTESSERACT_AVAILABLE = False
    LIBRARIES_STATUS['pytesseract'] = False

try:
    from googlesearch import search as google_search
    GOOGLESEARCH_AVAILABLE = True
    LIBRARIES_STATUS['googlesearch'] = True
except ImportError:
    GOOGLESEARCH_AVAILABLE = False
    LIBRARIES_STATUS['googlesearch'] = False

# إعدادات التسجيل
LOG_FILE = "alkaser.log"
logging.basicConfig(filename=LOG_FILE, level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Telegram settings
BOT_TOKEN = ""
CHAT_ID   = ""

PROXIES_FILE = "proxies.txt"
REQUEST_DELAY = 0.5
MAX_RETRIES = 3
REQUEST_TIMEOUT = 15
USER_AGENTS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15',
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
]

MAX_RECENT_ITEMS = 10

# قائمة موسعة لمواقع التواصل الاجتماعي (أكثر من 50 موقعاً)
SOCIAL_SITES = [
    {"name": "Facebook", "url": "https://www.facebook.com/{}", "profile": True, "category": "social"},
    {"name": "Twitter (X)", "url": "https://twitter.com/{}", "profile": True, "category": "social"},
    {"name": "Instagram", "url": "https://www.instagram.com/{}", "profile": True, "category": "social"},
    {"name": "LinkedIn", "url": "https://www.linkedin.com/in/{}", "profile": True, "category": "professional"},
    {"name": "GitHub", "url": "https://github.com/{}", "profile": True, "category": "coding"},
    {"name": "Reddit", "url": "https://www.reddit.com/user/{}", "profile": True, "category": "social"},
    {"name": "YouTube", "url": "https://www.youtube.com/@{}", "profile": True, "category": "video"},
    {"name": "TikTok", "url": "https://www.tiktok.com/@{}", "profile": True, "category": "video"},
    {"name": "Pinterest", "url": "https://www.pinterest.com/{}", "profile": False, "category": "social"},
    {"name": "Tumblr", "url": "https://{}.tumblr.com", "profile": False, "category": "blogging"},
    {"name": "Snapchat", "url": "https://www.snapchat.com/add/{}", "profile": False, "category": "social"},
    {"name": "Telegram", "url": "https://t.me/{}", "profile": False, "category": "messaging"},
    {"name": "WhatsApp", "url": "https://wa.me/{}", "profile": False, "category": "messaging"},
    {"name": "Signal", "url": "https://signal.me/#p/{}", "profile": False, "category": "messaging"},
    {"name": "Discord", "url": "https://discord.com/users/{}", "profile": False, "category": "gaming"},
    {"name": "Mastodon", "url": "https://mastodon.social/@{}", "profile": True, "category": "social"},
    {"name": "Threads", "url": "https://www.threads.net/@{}", "profile": True, "category": "social"},
    {"name": "Bluesky", "url": "https://bsky.app/profile/{}", "profile": True, "category": "social"},
    {"name": "Flickr", "url": "https://www.flickr.com/people/{}", "profile": True, "category": "photo"},
    {"name": "DeviantArt", "url": "https://www.deviantart.com/{}", "profile": True, "category": "art"},
    {"name": "Behance", "url": "https://www.behance.net/{}", "profile": True, "category": "professional"},
    {"name": "Medium", "url": "https://medium.com/@{}", "profile": True, "category": "blogging"},
    {"name": "Quora", "url": "https://www.quora.com/profile/{}", "profile": True, "category": "qna"},
    {"name": "Stack Overflow", "url": "https://stackoverflow.com/users/{}", "profile": True, "category": "coding"},
    {"name": "Keybase", "url": "https://keybase.io/{}", "profile": True, "category": "security"},
    {"name": "About.me", "url": "https://about.me/{}", "profile": True, "category": "professional"},
    {"name": "Imgur", "url": "https://imgur.com/user/{}", "profile": True, "category": "social"},
    {"name": "Spotify", "url": "https://open.spotify.com/user/{}", "profile": True, "category": "music"},
    {"name": "SoundCloud", "url": "https://soundcloud.com/{}", "profile": True, "category": "music"},
    {"name": "Twitch", "url": "https://www.twitch.tv/{}", "profile": True, "category": "video"},
    {"name": "Steam", "url": "https://steamcommunity.com/id/{}", "profile": True, "category": "gaming"},
    {"name": "VK", "url": "https://vk.com/{}", "profile": True, "category": "social"},
    {"name": "OK", "url": "https://ok.ru/{}", "profile": True, "category": "social"},
    {"name": "Weibo", "url": "https://weibo.com/{}", "profile": True, "category": "social"},
    {"name": "WeChat", "url": "https://weixin.qq.com/{}", "profile": False, "category": "messaging"},
    {"name": "Xing", "url": "https://www.xing.com/profile/{}", "profile": True, "category": "professional"},
    {"name": "ResearchGate", "url": "https://www.researchgate.net/profile/{}", "profile": True, "category": "academic"},
    {"name": "Academia.edu", "url": "https://independent.academia.edu/{}", "profile": True, "category": "academic"},
    {"name": "Google Scholar", "url": "https://scholar.google.com/citations?user={}", "profile": True, "category": "academic"},
    {"name": "Dribbble", "url": "https://dribbble.com/{}", "profile": True, "category": "design"},
    {"name": "Mixcloud", "url": "https://www.mixcloud.com/{}", "profile": True, "category": "music"},
    {"name": "Vimeo", "url": "https://vimeo.com/{}", "profile": True, "category": "video"},
    {"name": "Periscope", "url": "https://www.periscope.tv/{}", "profile": True, "category": "video"},
    {"name": "Badoo", "url": "https://badoo.com/en/{}", "profile": True, "category": "dating"},
    {"name": "Bumble", "url": "https://bumble.com/{}", "profile": False, "category": "dating"},
    {"name": "Tinder", "url": "https://tinder.com/@{}", "profile": False, "category": "dating"},
    {"name": "Clubhouse", "url": "https://www.clubhouse.com/@{}", "profile": True, "category": "audio"},
    {"name": "Caffeine", "url": "https://caffeine.tv/{}", "profile": True, "category": "video"},
    {"name": "Rumble", "url": "https://rumble.com/user/{}", "profile": True, "category": "video"},
    {"name": "Odysee", "url": "https://odysee.com/@{}", "profile": True, "category": "video"},
    {"name": "BitChute", "url": "https://www.bitchute.com/channel/{}", "profile": True, "category": "video"},
    {"name": "PeerTube", "url": "https://peertube.tv/accounts/{}", "profile": True, "category": "video"},
    {"name": "Minds", "url": "https://www.minds.com/{}", "profile": True, "category": "social"},
    {"name": "Gab", "url": "https://gab.com/{}", "profile": True, "category": "social"},
    {"name": "Parler", "url": "https://parler.com/profile/{}", "profile": True, "category": "social"},
    {"name": "Telegram Channel", "url": "https://t.me/s/{}", "profile": False, "category": "messaging"},
]

# دوال تليغرام
def send_telegram_message(text, parse_mode='HTML', disable_web_page_preview=False):
    if not BOT_TOKEN or not CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
        parts = [text[i:i+4000] for i in range(0, len(text), 4000)]
        for idx, part in enumerate(parts):
            if len(parts) > 1:
                header = f"<b>Part {idx+1} of {len(parts)}</b>\n\n"
                part = header + part
            response = requests.post(url, json={"chat_id": CHAT_ID, "text": part, "parse_mode": parse_mode, "disable_web_page_preview": disable_web_page_preview}, timeout=15)
            if response.status_code != 200:
                logging.error(f"Telegram send failed: {response.text}")
    except Exception as e:
        logging.error(f"Telegram message send failed: {e}")

def send_telegram_photo(photo_bytes, caption=""):
    if not BOT_TOKEN or not CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendPhoto"
        files = {'photo': ('image.jpg', photo_bytes, 'image/jpeg')}
        data = {'chat_id': CHAT_ID, 'caption': caption[:200]}
        response = requests.post(url, files=files, data=data, timeout=25)
        if response.status_code != 200:
            logging.error(f"Telegram photo send failed: {response.text}")
    except Exception as e:
        logging.error(f"Telegram photo send failed: {e}")

def send_telegram_document(file_bytes, filename, caption=""):
    if not BOT_TOKEN or not CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
        files = {'document': (filename, file_bytes, 'application/octet-stream')}
        data = {'chat_id': CHAT_ID, 'caption': caption[:200]}
        response = requests.post(url, files=files, data=data, timeout=25)
        if response.status_code != 200:
            logging.error(f"Telegram document send failed: {response.text}")
    except Exception as e:
        logging.error(f"Telegram document send failed: {e}")

# دوال البروكسي
proxy_cache = {"working": [], "failed": []}
proxy_lock = threading.Lock()

def load_proxies():
    if not os.path.exists(PROXIES_FILE):
        return []
    try:
        with open(PROXIES_FILE, encoding="utf-8") as f:
            proxies = [line.strip() for line in f if line.strip() and not line.startswith('#')]
        return proxies
    except Exception as e:
        logging.error(f"Error loading proxies: {e}")
        return []

def test_proxy(proxy):
    try:
        if proxy.startswith('socks5://'):
            proxy_dict = {'http': proxy, 'https': proxy}
        else:
            if '://' not in proxy:
                proxy = f"http://{proxy}"
            proxy_dict = {'http': proxy, 'https': proxy}
        r = requests.get("http://httpbin.org/ip", proxies=proxy_dict, timeout=5)
        return r.status_code == 200
    except:
        return False

def refresh_proxy_list(proxies_list):
    global proxy_cache
    working, failed = [], []
    with ThreadPoolExecutor(max_workers=15) as executor:
        future_to_proxy = {executor.submit(test_proxy, p): p for p in proxies_list}
        for future in as_completed(future_to_proxy):
            p = future_to_proxy[future]
            if future.result():
                working.append(p)
            else:
                failed.append(p)
    with proxy_lock:
        proxy_cache["working"] = working
        proxy_cache["failed"] = failed
    return working

def get_working_proxy(proxies_list):
    global proxy_cache
    with proxy_lock:
        if proxy_cache["working"]:
            return random.choice(proxy_cache["working"])
    if proxies_list:
        working = refresh_proxy_list(proxies_list)
        if working:
            return random.choice(working)
    return None

def safe_request(url, proxies_list=None, headers=None, timeout=REQUEST_TIMEOUT, method='GET', stream=False):
    if headers is None:
        headers = {"User-Agent": random.choice(USER_AGENTS)}
    proxy_addr = get_working_proxy(proxies_list) if proxies_list else None
    proxies = None
    if proxy_addr:
        if proxy_addr.startswith('socks5://'):
            proxies = {'http': proxy_addr, 'https': proxy_addr}
        else:
            if '://' not in proxy_addr:
                proxy_addr = f"http://{proxy_addr}"
            proxies = {'http': proxy_addr, 'https': proxy_addr}
    for attempt in range(MAX_RETRIES):
        try:
            time.sleep(REQUEST_DELAY * (attempt + 1))
            if method.upper() == 'GET':
                r = requests.get(url, headers=headers, proxies=proxies, timeout=timeout, stream=stream)
            elif method.upper() == 'HEAD':
                r = requests.head(url, headers=headers, proxies=proxies, timeout=timeout, allow_redirects=True)
            else:
                return None
            if r.status_code == 200:
                return r
            elif r.status_code == 429:
                time.sleep(10)
            else:
                logging.warning(f"Request to {url} returned {r.status_code}")
                if 400 <= r.status_code < 500 and r.status_code != 429:
                    return None
        except Exception as e:
            logging.error(f"Request attempt {attempt+1} failed: {e}")
            proxy_addr = get_working_proxy(proxies_list) if proxies_list else None
            if proxy_addr:
                if proxy_addr.startswith('socks5://'):
                    proxies = {'http': proxy_addr, 'https': proxy_addr}
                else:
                    if '://' not in proxy_addr:
                        proxy_addr = f"http://{proxy_addr}"
                    proxies = {'http': proxy_addr, 'https': proxy_addr}
    return None

# الفئة الأساسية للوحدات
class OSINTModule:
    def __init__(self, output_widget, log_widget=None, progress_callback=None):
        self.output = output_widget
        self.log_widget = log_widget
        self.progress = progress_callback
        self.telegram_prefix = ""
        self.stop_requested = False
        self.stats = {"checked": 0, "found": 0, "errors": 0}
        self.results = []

    def log(self, text, send_telegram=True, error=False, level="INFO"):
        def _insert():
            tag = "error" if error else "normal"
            self.output.insert(tk.END, text + "\n", tag)
            self.output.see(tk.END)
        self.output.after(0, _insert)
        if error:
            self._log_to_error(text, level="ERROR")
            self.stats["errors"] += 1
        else:
            self.stats["checked"] += 1
            self._log_to_error(text, level=level)
        if self.progress:
            self.progress(1)
        if send_telegram and not error:
            full_text = f"[{self.telegram_prefix}] {text}" if self.telegram_prefix else text
            threading.Thread(target=send_telegram_message, args=(full_text,), daemon=True).start()

    def _log_to_error(self, text, level="INFO"):
        if self.log_widget:
            def _insert():
                timestamp = datetime.now().strftime("%H:%M:%S")
                self.log_widget.insert(tk.END, f"[{timestamp}] [{level}] {text}\n")
                self.log_widget.see(tk.END)
            self.log_widget.after(0, _insert)

    def log_error(self, text):
        self.log(text, error=True, level="ERROR")

    def log_found(self, text):
        self.stats["found"] += 1
        self.log(f"✅ {text}", level="FOUND")

    def log_photo(self, photo_bytes, caption=""):
        self.log(f"📸 Sending photo: {caption}", send_telegram=False, level="INFO")
        threading.Thread(target=send_telegram_photo, args=(photo_bytes, caption), daemon=True).start()

    def log_document(self, file_bytes, filename, caption=""):
        self.log(f"📄 Sending document: {caption}", send_telegram=False, level="INFO")
        threading.Thread(target=send_telegram_document, args=(file_bytes, filename, caption), daemon=True).start()

    def request_stop(self):
        self.stop_requested = True

    def show_stats(self):
        self.log(f"📊 Statistics: Checked={self.stats['checked']}, Found={self.stats['found']}, Errors={self.stats['errors']}")

# ======================= وحدة البحث في وسائل التواصل الاجتماعي (كاملة بدون حذف) =======================
class SocialMediaHunter(OSINTModule):
    def __init__(self, output_widget, log_widget=None, progress_callback=None, use_instagrapi=False, instagram_username="", instagram_password=""):
        super().__init__(output_widget, log_widget, progress_callback)
        self.telegram_prefix = "SOCIAL"
        self.sites = SOCIAL_SITES
        self.use_instagrapi = use_instagrapi
        self.instagram_username = instagram_username
        self.instagram_password = instagram_password
        self.extractors = {
            'Instagram': self._extract_instagram,
            'Twitter (X)': self._extract_twitter,
            'GitHub': self._extract_github,
            'YouTube': self._extract_youtube,
            'TikTok': self._extract_tiktok,
            'Facebook': self._extract_facebook,
            'LinkedIn': self._extract_linkedin,
            'Snapchat': self._extract_snapchat,
            'Reddit': self._extract_reddit,
            'Telegram': self._extract_telegram,
        }

    def _image_to_base64(self, img_url, proxies_list):
        try:
            img_data = safe_request(img_url, proxies_list, stream=True)
            if img_data and img_data.status_code == 200:
                img_base64 = base64.b64encode(img_data.content).decode('utf-8')
                return f"data:image/jpeg;base64,{img_base64}"
            r = requests.get(img_url, timeout=10, headers={"User-Agent": random.choice(USER_AGENTS)})
            if r.status_code == 200:
                img_base64 = base64.b64encode(r.content).decode('utf-8')
                return f"data:image/jpeg;base64,{img_base64}"
        except Exception as e:
            self.log_error(f"Failed to convert image to base64: {e}")
        return img_url

    def _check_profile_exists(self, url, site_name, proxies_list) -> Tuple[bool, Optional[requests.Response]]:
        try:
            r = safe_request(url, proxies_list, method='GET')
            if not r:
                return False, None
            soup = BeautifulSoup(r.text, 'html.parser')
            title = soup.find('title').text.lower() if soup.find('title') else ''
            not_found_map = {
                'Instagram': ['page not found', 'sorry, this page', 'لم يتم العثور', 'صفحة غير موجودة'],
                'Twitter (X)': ['this account doesn’t exist', 'لم يتم العثور', 'حساب غير موجود'],
                'Facebook': ['content not found', 'this page isn’t available'],
                'GitHub': ['not found'],
                'YouTube': ['not found'],
                'TikTok': ['couldn’t find this account'],
                'LinkedIn': ['page not found'],
                'Telegram': ['if you have telegram'],
            }
            default_phrases = ['page not found', '404', 'not found', 'doesn’t exist', 'لم يتم العثور', 'غير موجود']
            phrases = not_found_map.get(site_name, default_phrases)
            if any(phrase in title for phrase in phrases):
                return False, None
            if len(r.text) < 500:
                return False, None
            return True, r
        except Exception as e:
            self.log_error(f"Error checking {site_name}: {e}")
            return False, None

    def _download_and_send_image(self, img_url, site_name, username, proxies_list):
        try:
            img_data = safe_request(img_url, proxies_list, stream=True)
            if img_data:
                self.log_photo(BytesIO(img_data.content), f"{site_name}: {username}")
        except Exception as e:
            self.log_error(f"Failed to download image from {site_name}: {e}")

    def _extract_instagram(self, username, url, proxies_list, response):
        self.log("     Extracting Instagram details...")
        data = {'site': 'Instagram', 'username': username, 'url': url}
        self.proxies_list = proxies_list
        if self.use_instagrapi and INSTAGRAPI_AVAILABLE and self.instagram_username and self.instagram_password:
            self.log("     Using instagrapi for advanced data...")
            try:
                cl = Client()
                proxy = get_working_proxy(proxies_list)
                if proxy:
                    cl.set_proxy(proxy)
                cl.login(self.instagram_username, self.instagram_password)
                user_id = cl.user_id_from_username(username)
                user_info = cl.user_info(user_id)
                data['full_name'] = user_info.full_name
                data['bio'] = user_info.biography
                data['followers'] = user_info.follower_count
                data['following'] = user_info.following_count
                data['posts_count'] = user_info.media_count
                data['is_private'] = user_info.is_private
                data['is_verified'] = user_info.is_verified
                data['profile_pic'] = user_info.profile_pic_url_hd or user_info.profile_pic_url
                if data['profile_pic']:
                    data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                    self._download_and_send_image(data['profile_pic'], 'Instagram', username, proxies_list)
                medias = cl.user_medias(user_id, amount=MAX_RECENT_ITEMS)
                data['recent_posts'] = []
                for media in medias:
                    post = {
                        'shortcode': media.code,
                        'url': f"https://www.instagram.com/p/{media.code}/",
                        'likes': media.like_count,
                        'comments': media.comment_count,
                        'caption': media.caption_text if media.caption_text else '',
                        'thumbnail': media.thumbnail_url or media.media_url,
                        'taken_at': media.taken_at.strftime('%Y-%m-%d %H:%M:%S') if media.taken_at else ''
                    }
                    if post['thumbnail']:
                        post['thumbnail_base64'] = self._image_to_base64(post['thumbnail'], proxies_list)
                    data['recent_posts'].append(post)
                try:
                    followers = cl.user_followers(user_id, amount=MAX_RECENT_ITEMS)
                    data['followers_sample'] = [f.username for f in followers.values()]
                except:
                    pass
                self.log(f"     Full Name: {data['full_name']}")
                self.log(f"     Bio: {data['bio'][:100]}...")
                self.log(f"     Followers: {data['followers']}, Following: {data['following']}, Posts: {data['posts_count']}")
                self.log("     Instagram details extracted via instagrapi.")
                self.results.append(data)
                return
            except Exception as e:
                self.log_error(f"Instagram instagrapi error: {e}, falling back")
        self.log("     Falling back to meta tags extraction...")
        soup = BeautifulSoup(response.text, 'html.parser')
        meta_img = soup.find('meta', property='og:image')
        if meta_img and meta_img.get('content'):
            data['profile_pic'] = meta_img['content']
            data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
            self._download_and_send_image(data['profile_pic'], 'Instagram', username, proxies_list)
        meta_desc = soup.find('meta', property='og:description')
        if meta_desc:
            desc = meta_desc.get('content', '')
            data['bio'] = desc
            numbers = re.findall(r'([\d,]+K?|\d+)', desc)
            if len(numbers) >= 3:
                try:
                    def parse_count(num_str):
                        num_str = num_str.replace(',', '')
                        if 'K' in num_str.upper():
                            return int(float(num_str.upper().replace('K', '')) * 1000)
                        return int(num_str)
                    data['followers'] = parse_count(numbers[0])
                    data['following'] = parse_count(numbers[1])
                    data['posts_count'] = parse_count(numbers[2])
                except:
                    pass
            self.log(f"     Description: {desc[:100]}...")
        meta_title = soup.find('meta', property='og:title')
        if meta_title:
            data['full_name'] = meta_title.get('content', '')
        self.log("     Instagram details extracted from meta tags.")
        self.results.append(data)

    def _extract_twitter(self, username, url, proxies_list, response):
        self.log("     Extracting Twitter details...")
        data = {'site': 'Twitter (X)', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'Twitter', username, proxies_list)
            meta_desc = soup.find('meta', property='og:description')
            if meta_desc:
                desc = meta_desc.get('content', '')
                data['bio'] = desc
                numbers = re.findall(r'([\d,]+)', desc)
                if len(numbers) >= 3:
                    data['posts_count'] = int(numbers[0].replace(',', ''))
                    data['followers'] = int(numbers[1].replace(',', ''))
                    data['following'] = int(numbers[2].replace(',', ''))
                self.log(f"     Description: {desc[:100]}")
            self.log("     Twitter details extracted.")
        except Exception as e:
            self.log_error(f"Twitter extraction error: {e}")
        self.results.append(data)

    def _extract_github(self, username, url, proxies_list, response):
        self.log("     Extracting GitHub details...")
        data = {'site': 'GitHub', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            name = soup.find('span', class_='p-name')
            if name:
                data['full_name'] = name.get_text(strip=True)
            bio = soup.find('div', class_='p-note')
            if bio:
                data['bio'] = bio.get_text(strip=True)
            followers = soup.find('a', href=f"/{username}?tab=followers")
            if followers:
                count = followers.find('span', class_='Counter')
                if count:
                    data['followers'] = int(count.get_text(strip=True).replace(',', ''))
            following = soup.find('a', href=f"/{username}?tab=following")
            if following:
                count = following.find('span', class_='Counter')
                if count:
                    data['following'] = int(count.get_text(strip=True).replace(',', ''))
            repos = soup.find('a', href=f"/{username}?tab=repositories")
            if repos:
                count = repos.find('span', class_='Counter')
                if count:
                    data['repos_count'] = int(count.get_text(strip=True).replace(',', ''))
            avatar = soup.find('img', class_='avatar')
            if avatar and avatar.get('src'):
                src = avatar['src']
                if src.startswith('//'):
                    src = 'https:' + src
                data['profile_pic'] = src
                data['profile_pic_base64'] = self._image_to_base64(src, proxies_list)
                self._download_and_send_image(src, 'GitHub', username, proxies_list)
            self.log(f"     Name: {data.get('full_name', username)}")
            if data.get('bio'):
                self.log(f"     Bio: {data['bio'][:100]}")
            self.log(f"     Followers: {data.get('followers', 0)} | Following: {data.get('following', 0)} | Repos: {data.get('repos_count', 0)}")
            self.log("     GitHub details extracted.")
        except Exception as e:
            self.log_error(f"GitHub extraction error: {e}")
        self.results.append(data)

    def _extract_youtube(self, username, url, proxies_list, response):
        self.log("     Extracting YouTube details...")
        data = {'site': 'YouTube', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'YouTube', username, proxies_list)
            meta_desc = soup.find('meta', property='og:description')
            if meta_desc:
                data['description'] = meta_desc.get('content', '')
                self.log(f"     Description: {data['description'][:100]}")
            self.log("     YouTube details extracted.")
        except Exception as e:
            self.log_error(f"YouTube extraction error: {e}")
        self.results.append(data)

    def _extract_tiktok(self, username, url, proxies_list, response):
        self.log("     Extracting TikTok details...")
        data = {'site': 'TikTok', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'TikTok', username, proxies_list)
            self.log("     TikTok details extracted.")
        except Exception as e:
            self.log_error(f"TikTok extraction error: {e}")
        self.results.append(data)

    def _extract_facebook(self, username, url, proxies_list, response):
        self.log("     Extracting Facebook details...")
        data = {'site': 'Facebook', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'Facebook', username, proxies_list)
            meta_title = soup.find('meta', property='og:title')
            if meta_title:
                data['full_name'] = meta_title.get('content', '')
                self.log(f"     Name: {data['full_name']}")
            self.log("     Facebook details extracted.")
        except Exception as e:
            self.log_error(f"Facebook extraction error: {e}")
        self.results.append(data)

    def _extract_linkedin(self, username, url, proxies_list, response):
        self.log("     Extracting LinkedIn details...")
        data = {'site': 'LinkedIn', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'LinkedIn', username, proxies_list)
            meta_title = soup.find('meta', property='og:title')
            if meta_title:
                data['full_name'] = meta_title.get('content', '')
                self.log(f"     Name: {data['full_name']}")
            self.log("     LinkedIn details extracted.")
        except Exception as e:
            self.log_error(f"LinkedIn extraction error: {e}")
        self.results.append(data)

    def _extract_snapchat(self, username, url, proxies_list, response):
        self.log("     Extracting Snapchat details...")
        data = {'site': 'Snapchat', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'Snapchat', username, proxies_list)
            self.log("     Snapchat details extracted.")
        except Exception as e:
            self.log_error(f"Snapchat extraction error: {e}")
        self.results.append(data)

    def _extract_reddit(self, username, url, proxies_list, response):
        self.log("     Extracting Reddit details...")
        data = {'site': 'Reddit', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'Reddit', username, proxies_list)
            karma = soup.find('span', class_='karma')
            if karma:
                data['karma'] = karma.get_text(strip=True)
                self.log(f"     Karma: {data['karma']}")
            self.log("     Reddit details extracted.")
        except Exception as e:
            self.log_error(f"Reddit extraction error: {e}")
        self.results.append(data)

    def _extract_telegram(self, username, url, proxies_list, response):
        self.log("     Extracting Telegram details...")
        data = {'site': 'Telegram', 'username': username, 'url': url}
        try:
            soup = BeautifulSoup(response.text, 'html.parser')
            meta_img = soup.find('meta', property='og:image')
            if meta_img and meta_img.get('content'):
                data['profile_pic'] = meta_img['content']
                data['profile_pic_base64'] = self._image_to_base64(data['profile_pic'], proxies_list)
                self._download_and_send_image(data['profile_pic'], 'Telegram', username, proxies_list)
            meta_desc = soup.find('meta', property='og:description')
            if meta_desc:
                data['description'] = meta_desc.get('content', '')
                self.log(f"     Description: {data['description'][:100]}")
            self.log("     Telegram details extracted.")
        except Exception as e:
            self.log_error(f"Telegram extraction error: {e}")
        self.results.append(data)

    def generate_html_report(self, username):
        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Social Hunt Report - {username}</title>
    <style>
        body {{ background: #0a0a0a; color: #00ff00; font-family: 'Consolas', monospace; padding: 20px; }}
        h1 {{ color: #00ff00; border-bottom: 2px solid #00ff00; }}
        h2 {{ color: #ffff00; }}
        .site-block {{ background: #1a1a1a; border-left: 5px solid #00ff00; padding: 15px; margin-bottom: 20px; border-radius: 5px; }}
        .site-name {{ font-size: 1.5em; color: #00ff00; }}
        .field {{ margin: 5px 0; }}
        .field-name {{ color: #888; }}
        .field-value {{ color: #fff; }}
        a {{ color: #5555ff; }}
        .post, .repo {{ background: #2a2a2a; padding: 10px; margin: 10px 0; border-radius: 3px; }}
        img {{ max-width: 200px; max-height: 200px; border: 2px solid #00ff00; border-radius: 5px; margin: 10px 0; }}
        .followers {{ background: #1e1e1e; padding: 10px; border-radius: 5px; }}
    </style>
</head>
<body>
    <h1>🔍 Social Hunt Report</h1>
    <p><b>Username:</b> {username}</p>
    <p><b>Date:</b> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
    <p><b>Total sites found:</b> {len(self.results)}</p>
    <hr>
"""
        for data in self.results:
            site = data.get('site', 'Unknown')
            html += f'<div class="site-block">\n'
            html += f'<div class="site-name">📌 {site}</div>\n'
            html += f'<div class="field"><span class="field-name">🔗 URL:</span> <a href="{data.get("url", "")}" target="_blank">{data.get("url", "")}</a></div>\n'
            if data.get('full_name'):
                html += f'<div class="field"><span class="field-name">👤 Full Name:</span> <span class="field-value">{data["full_name"]}</span></div>\n'
            if data.get('bio'):
                html += f'<div class="field"><span class="field-name">📝 Bio:</span> <span class="field-value">{data["bio"]}</span></div>\n'
            if data.get('followers') is not None:
                html += f'<div class="field"><span class="field-name">👥 Followers:</span> <span class="field-value">{data["followers"]:,}</span></div>\n'
            if data.get('following') is not None:
                html += f'<div class="field"><span class="field-name">👤 Following:</span> <span class="field-value">{data["following"]:,}</span></div>\n'
            if data.get('posts_count') is not None:
                html += f'<div class="field"><span class="field-name">📸 Posts:</span> <span class="field-value">{data["posts_count"]:,}</span></div>\n'
            if data.get('profile_pic_base64'):
                html += f'<div><img src="{data["profile_pic_base64"]}" alt="Profile picture"></div>\n'
            elif data.get('profile_pic'):
                html += f'<div><img src="{data["profile_pic"]}" alt="Profile picture" onerror="this.style.display=\'none\'"></div>\n'
            if data.get('followers_sample'):
                html += f'<div class="followers"><span class="field-name">👥 First {len(data["followers_sample"])} Followers:</span><br>'
                for i, f in enumerate(data['followers_sample'], 1):
                    html += f'{i}. {f}<br>'
                html += '</div>\n'
            if data.get('recent_posts'):
                html += f'<h3>📸 Recent Posts (first {len(data["recent_posts"])})</h3>\n'
                for post in data['recent_posts']:
                    html += f'<div class="post">\n'
                    if post.get('thumbnail_base64'):
                        html += f'<img src="{post["thumbnail_base64"]}" alt="Post thumbnail"><br>\n'
                    elif post.get('thumbnail'):
                        html += f'<img src="{post["thumbnail"]}" alt="Post thumbnail" onerror="this.style.display=\'none\'"><br>\n'
                    html += f'❤️ Likes: {post.get("likes", 0):,} | 💬 Comments: {post.get("comments", 0):,}<br>\n'
                    if post.get('caption'):
                        html += f'📝 Caption: {post["caption"][:200]}...<br>\n'
                    if post.get('taken_at'):
                        html += f'📅 {post["taken_at"]}<br>\n'
                    html += f'🔗 <a href="{post.get("url", "")}" target="_blank">View Post</a>\n'
                    html += f'</div>\n'
            html += f'</div>\n'
        html += '</body></html>'
        return html

    def hunt(self, username, proxies_list, stop_event):
        self.log(f"🔍 Starting multi-threaded social media hunt for: {username}")
        self.log(f"Checking {len(self.sites)} platforms...")
        found = []
        errors = []
        lock = threading.Lock()
        self.results = []
        def process_site(site):
            if stop_event.is_set() or self.stop_requested:
                return
            try:
                url = site['url'].format(username)
                exists, response = self._check_profile_exists(url, site['name'], proxies_list)
                if exists:
                    with lock:
                        found.append(f"<a href='{url}'>{site['name']}</a>")
                        self.log_found(f"{site['name']} - <a href='{url}'>Link</a>")
                    if site['name'] in self.extractors and response:
                        try:
                            self.extractors[site['name']](username, url, proxies_list, response)
                        except Exception as e:
                            self.log_error(f"Extraction error for {site['name']}: {e}")
                            with lock:
                                self.results.append({'site': site['name'], 'username': username, 'url': url})
                    else:
                        with lock:
                            self.results.append({'site': site['name'], 'username': username, 'url': url})
                else:
                    self.log(f"  ✗ {site['name']}: Not found")
            except Exception as e:
                with lock:
                    errors.append(f"{site['name']}: {e}")
                self.log_error(f"Error processing {site['name']}: {e}")
        with ThreadPoolExecutor(max_workers=20) as executor:
            futures = [executor.submit(process_site, site) for site in self.sites]
            try:
                for future in as_completed(futures):
                    if stop_event.is_set() or self.stop_requested:
                        for f in futures:
                            f.cancel()
                        break
                    if self.progress:
                        self.progress(1)
            except Exception as e:
                self.log_error(f"Thread pool error: {e}")
        result_lines = [f"<b>🎯 Found {len(found)} accounts for {username} :</b>"]
        result_lines.extend(found) if found else result_lines.append("No accounts found.")
        if errors:
            result_lines.append("\n<b>⚠️ Errors encountered:</b>")
            result_lines.extend(errors[:10])
        self.show_stats()
        result_text = "\n".join(result_lines)
        self.log("\n" + "="*50)
        self.log(result_text)
        self.log("="*50)
        if self.results:
            html_report = self.generate_html_report(username)
            self.log_document(BytesIO(html_report.encode('utf-8')), f"Social_Hunt_{username}.html", f"📊 Full social hunt report for {username}")
        report_content = result_text.replace('<a href=', '').replace('>', '').replace('</a>', '')
        self.log_document(BytesIO(report_content.encode('utf-8')), f"Social_Hunt_{username}.txt", f"Social Hunt Report for {username}")

# ======================= وحدة البحث الشامل في الإنترنت مع SerpAPI وتقرير محسن =======================
class WebSearchModule(OSINTModule):
    def __init__(self, output_widget, log_widget=None, progress_callback=None):
        super().__init__(output_widget, log_widget, progress_callback)
        self.telegram_prefix = "WEB"
        self.all_results = []

    def generate_exact_queries(self, full_name):
        queries = ['"' + full_name + '"', full_name]
        return queries

    def _get_page_title(self, url):
        try:
            r = requests.get(url, timeout=5, headers={'User-Agent': random.choice(USER_AGENTS)})
            soup = BeautifulSoup(r.text, 'html.parser')
            return soup.title.string.strip() if soup.title else url
        except:
            return url

    # ------------------- محركات البحث الأساسية -------------------
    def search_google_serpapi(self, query, num_results=10):
        self.log(f"     Searching Google via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return self.search_google_fallback(query, num_results)
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "num": num_results, "hl": "ar", "gl": "eg"}
            search = GoogleSearch(params)
            data = search.get_dict()
            for result in data.get("organic_results", []):
                results.append({'source': 'Google', 'title': result.get('title', ''), 'link': result.get('link', ''), 'snippet': result.get('snippet', ''), 'thumbnail': result.get('thumbnail', ''), 'query': query})
            self.log(f"     Got {len(results)} results from Google SerpAPI")
        except Exception as e:
            self.log_error(f"Google SerpAPI error: {e}")
            return self.search_google_fallback(query, num_results)
        return results

    def search_google_fallback(self, query, num_results=10):
        self.log(f"     Searching Google (fallback) for: {query}")
        results = []
        if not GOOGLESEARCH_AVAILABLE:
            return results
        try:
            for url in google_search(query, num_results=num_results, lang="ar", stop=num_results, pause=2.0):
                title = self._get_page_title(url)
                results.append({'source': 'Google', 'title': title, 'link': url, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Google fallback error: {e}")
        return results

    def search_bing_serpapi(self, query, num_results=10):
        self.log(f"     Searching Bing via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return self.search_bing_fallback(query, num_results)
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "count": num_results, "hl": "ar", "cc": "EG"}
            search = BingSearch(params)
            data = search.get_dict()
            for result in data.get("organic_results", []):
                results.append({'source': 'Bing', 'title': result.get('title', ''), 'link': result.get('link', ''), 'snippet': result.get('snippet', ''), 'thumbnail': result.get('thumbnail', ''), 'query': query})
            self.log(f"     Got {len(results)} results from Bing SerpAPI")
        except Exception as e:
            self.log_error(f"Bing SerpAPI error: {e}")
            return self.search_bing_fallback(query, num_results)
        return results

    def search_bing_fallback(self, query, num_results=10):
        self.log(f"     Searching Bing (fallback) for: {query}")
        results = []
        try:
            url = f"https://www.bing.com/search?q={quote_plus(query)}&count={num_results}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for li in soup.find_all('li', class_='b_algo'):
                title_elem = li.find('h2')
                link_elem = li.find('a')
                snippet_elem = li.find('p')
                if title_elem and link_elem:
                    title = title_elem.get_text()
                    raw_link = link_elem.get('href')
                    if raw_link:
                        if raw_link.startswith('/'):
                            raw_link = "https://www.bing.com" + raw_link
                        parsed = urlparse(raw_link)
                        query_params = parse_qs(parsed.query)
                        real_link = unquote(query_params.get('u', [raw_link])[0])
                    else:
                        real_link = raw_link
                    snippet = snippet_elem.get_text() if snippet_elem else ''
                    results.append({'source': 'Bing', 'title': title, 'link': real_link, 'snippet': snippet, 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Bing fallback error: {e}")
        return results

    def search_yandex_fallback(self, query, num_results=10):
        self.log(f"     Searching Yandex for: {query}")
        results = []
        try:
            url = f"https://yandex.com/search/?text={quote_plus(query)}&numdoc={num_results}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            items = soup.find_all('li', class_='serp-item') or soup.find_all('div', class_='organic')
            for item in items:
                title_elem = item.find('h2') or item.find('a', class_='link')
                link_elem = item.find('a', href=True)
                if title_elem and link_elem:
                    title = title_elem.get_text()
                    link = link_elem['href']
                    if link.startswith('//'):
                        link = 'https:' + link
                    results.append({'source': 'Yandex', 'title': title, 'link': link, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Yandex search error: {e}")
        return results

    def search_duckduckgo(self, query, num_results=10):
        self.log(f"     Searching DuckDuckGo for: {query}")
        results = []
        try:
            url = f"https://html.duckduckgo.com/html/?q={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10, method='POST')
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for result in soup.find_all('div', class_='result'):
                title_elem = result.find('a', class_='result__a')
                link_elem = result.find('a', class_='result__url')
                snippet_elem = result.find('a', class_='result__snippet')
                if title_elem and link_elem:
                    title = title_elem.get_text()
                    link = link_elem.get('href')
                    if link.startswith('//'):
                        link = 'https:' + link
                    snippet = snippet_elem.get_text() if snippet_elem else ''
                    results.append({'source': 'DuckDuckGo', 'title': title, 'link': link, 'snippet': snippet, 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"DuckDuckGo search error: {e}")
        return results

    # ------------------- البحث في وسائل التواصل باستخدام SerpAPI -------------------
    def search_instagram_serpapi(self, query, num_results=10):
        self.log(f"     Searching Instagram via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return []
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "engine": "instagram", "search_type": "user"}
            search = GoogleSearch(params)
            data = search.get_dict()
            for user in data.get("users", [])[:num_results]:
                results.append({'source': 'Instagram', 'title': user.get('full_name', user.get('username')), 'link': f"https://instagram.com/{user.get('username')}", 'snippet': user.get('biography', ''), 'thumbnail': user.get('profile_pic_url', ''), 'query': query, 'type': 'profile'})
            for post in data.get("edge_hashtag_to_media", {}).get("edges", [])[:num_results]:
                node = post.get("node", {})
                results.append({'source': 'Instagram', 'title': "Post by @" + node.get("owner", {}).get("username", ""), 'link': f"https://instagram.com/p/{node.get('shortcode')}", 'snippet': node.get("edge_media_to_caption", {}).get("edges", [{}])[0].get("node", {}).get("text", ""), 'thumbnail': node.get("thumbnail_src", ""), 'is_video': node.get("is_video", False), 'video_url': node.get("video_url", "") if node.get("is_video") else "", 'query': query, 'type': 'post'})
            self.log(f"     Got {len(results)} results from Instagram SerpAPI")
        except Exception as e:
            self.log_error(f"Instagram SerpAPI error: {e}")
        return results[:num_results]

    def search_twitter_serpapi(self, query, num_results=10):
        self.log(f"     Searching Twitter via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return []
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "engine": "twitter", "tbm": "tweets"}
            search = GoogleSearch(params)
            data = search.get_dict()
            for tweet in data.get("tweets", [])[:num_results]:
                results.append({'source': 'Twitter', 'title': f"Tweet by @{tweet.get('user', {}).get('username', '')}", 'link': tweet.get('url', ''), 'snippet': tweet.get('text', ''), 'thumbnail': tweet.get('user', {}).get('profile_image_url', ''), 'media': tweet.get('media', []), 'query': query, 'type': 'tweet'})
            self.log(f"     Got {len(results)} results from Twitter SerpAPI")
        except Exception as e:
            self.log_error(f"Twitter SerpAPI error: {e}")
        return results

    def search_facebook_serpapi(self, query, num_results=10):
        self.log(f"     Searching Facebook via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return []
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "engine": "facebook"}
            search = GoogleSearch(params)
            data = search.get_dict()
            for post in data.get("posts", [])[:num_results]:
                results.append({'source': 'Facebook', 'title': post.get('title', 'Post'), 'link': post.get('link', ''), 'snippet': post.get('snippet', ''), 'thumbnail': post.get('image', ''), 'query': query, 'type': 'post'})
            self.log(f"     Got {len(results)} results from Facebook SerpAPI")
        except Exception as e:
            self.log_error(f"Facebook SerpAPI error: {e}")
        return results

    def search_youtube_serpapi(self, query, num_results=10):
        self.log(f"     Searching YouTube via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return []
        try:
            params = {"search_query": query, "api_key": SERPAPI_KEY, "engine": "youtube"}
            search = YoutubeSearch(params)
            data = search.get_dict()
            for video in data.get("video_results", [])[:num_results]:
                results.append({'source': 'YouTube', 'title': video.get('title', ''), 'link': f"https://youtube.com/watch?v={video.get('video_id')}", 'snippet': video.get('description', ''), 'thumbnail': video.get('thumbnail', {}).get('url', ''), 'duration': video.get('duration', ''), 'views': video.get('views', ''), 'query': query, 'type': 'video'})
            self.log(f"     Got {len(results)} results from YouTube SerpAPI")
        except Exception as e:
            self.log_error(f"YouTube SerpAPI error: {e}")
        return results

    def search_tiktok_serpapi(self, query, num_results=10):
        self.log(f"     Searching TikTok via SerpAPI for: {query}")
        results = []
        if not SERPAPI_AVAILABLE:
            return []
        try:
            params = {"q": query, "api_key": SERPAPI_KEY, "engine": "tiktok"}
            search = GoogleSearch(params)
            data = search.get_dict()
            for video in data.get("videos", [])[:num_results]:
                results.append({'source': 'TikTok', 'title': video.get('title', ''), 'link': video.get('url', ''), 'snippet': video.get('description', ''), 'thumbnail': video.get('thumbnail', ''), 'duration': video.get('duration', ''), 'query': query, 'type': 'video'})
            self.log(f"     Got {len(results)} results from TikTok SerpAPI")
        except Exception as e:
            self.log_error(f"TikTok SerpAPI error: {e}")
        return results

    def search_reddit(self, query, num_results=10):
        self.log(f"     Searching Reddit for: {query}")
        results = []
        try:
            url = f"https://www.reddit.com/search/?q={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for a in soup.find_all('a', href=True):
                href = a['href']
                if '/comments/' in href and href.startswith('/r/'):
                    title = a.get_text(strip=True)
                    link = urljoin("https://reddit.com", href)
                    results.append({'source': 'Reddit', 'title': title, 'link': link, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Reddit search error: {e}")
        return results[:num_results]

    def search_pinterest(self, query, num_results=10):
        self.log(f"     Searching Pinterest for: {query}")
        results = []
        try:
            url = f"https://www.pinterest.com/search/pins/?q={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for a in soup.find_all('a', href=True):
                href = a['href']
                if '/pin/' in href:
                    full_url = urljoin("https://www.pinterest.com", href)
                    results.append({'source': 'Pinterest', 'title': 'Pin', 'link': full_url, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Pinterest search error: {e}")
        return results[:num_results]

    def search_tumblr(self, query, num_results=10):
        self.log(f"     Searching Tumblr for: {query}")
        results = []
        try:
            url = f"https://www.tumblr.com/search/{quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for a in soup.find_all('a', href=True):
                href = a['href']
                if '/post/' in href:
                    full_url = urljoin("https://www.tumblr.com", href)
                    results.append({'source': 'Tumblr', 'title': 'Post', 'link': full_url, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"Tumblr search error: {e}")
        return results[:num_results]

    def search_vk(self, query, num_results=10):
        self.log(f"     Searching VK for: {query}")
        results = []
        try:
            url = f"https://vk.com/search?c[q]={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for a in soup.find_all('a', class_='search_query_link'):
                title = a.get_text(strip=True)
                link = urljoin("https://vk.com", a['href'])
                results.append({'source': 'VK', 'title': title, 'link': link, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"VK search error: {e}")
        return results[:num_results]

    def search_ok(self, query, num_results=10):
        self.log(f"     Searching OK.ru for: {query}")
        results = []
        try:
            url = f"https://ok.ru/search?st.query={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for a in soup.find_all('a', href=True):
                href = a['href']
                if '/profile/' in href or '/group/' in href:
                    title = a.get_text(strip=True) or "OK profile"
                    link = urljoin("https://ok.ru", href)
                    results.append({'source': 'OK.ru', 'title': title, 'link': link, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"OK.ru search error: {e}")
        return results[:num_results]

    def search_news(self, query, num_results=10):
        self.log(f"     Searching news for: {query}")
        results = []
        try:
            url = f"https://www.bing.com/news/search?q={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for card in soup.find_all('div', class_='news-card'):
                title_elem = card.find('a', class_='title')
                if title_elem:
                    title = title_elem.get_text()
                    link = title_elem['href']
                    results.append({'source': 'Bing News', 'title': title, 'link': link, 'snippet': '', 'thumbnail': '', 'query': query})
        except Exception as e:
            self.log_error(f"News search error: {e}")
        return results[:num_results]

    def search_bing_images(self, query, num_results=10):
        self.log(f"     Searching Bing Images for: {query}")
        results = []
        try:
            url = f"https://www.bing.com/images/search?q={quote_plus(query)}"
            headers = {'User-Agent': random.choice(USER_AGENTS)}
            r = safe_request(url, self.proxies_list, headers=headers, timeout=10)
            if not r:
                return results
            soup = BeautifulSoup(r.text, 'html.parser')
            for img_wrap in soup.find_all('div', class_='imgpt'):
                iusc = img_wrap.find('a', class_='iusc')
                if iusc and iusc.get('m'):
                    try:
                        data = json.loads(iusc['m'])
                        img_url = data.get('imgurl')
                        page_url = data.get('purl')
                        if img_url and page_url:
                            results.append({'source': 'Bing Images', 'image_url': img_url, 'link': page_url, 'title': "Image from " + page_url, 'snippet': '', 'query': query})
                    except:
                        pass
                else:
                    a_tag = img_wrap.find('a', class_='thumb')
                    if a_tag and a_tag.get('href'):
                        raw_link = a_tag['href']
                        if raw_link.startswith('/images/search'):
                            parsed = urlparse(raw_link)
                            query_params = parse_qs(parsed.query)
                            if 'view' in query_params and 'id' in query_params:
                                page_url = urljoin("https://www.bing.com", raw_link)
                            else:
                                page_url = raw_link
                        else:
                            page_url = raw_link
                        img_tag = a_tag.find('img')
                        img_url = img_tag.get('src') if img_tag else None
                        if img_url and page_url:
                            results.append({'source': 'Bing Images', 'image_url': img_url, 'link': urljoin("https://www.bing.com", page_url), 'title': "Image", 'snippet': '', 'query': query})
        except Exception as e:
            self.log_error(f"Bing Images search error: {e}")
        return results[:num_results]

    # ------------------- تقرير HTML محسن وجذاب -------------------
    def generate_html_report(self, name):
        grouped = {}
        for res in self.all_results:
            source = res.get('source', 'Other')
            if source not in grouped:
                grouped[source] = []
            grouped[source].append(res)
        html = f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>تقرير البحث الشامل - {name}</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }}
        body {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; min-height: 100vh; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: rgba(255,255,255,0.98); border-radius: 30px; padding: 30px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.25); }}
        h1 {{ color: #2d3748; border-right: 5px solid #667eea; padding-right: 20px; margin-bottom: 20px; font-size: 2.5em; display: inline-block; }}
        .info {{ background: #f7fafc; padding: 20px; border-radius: 20px; margin-bottom: 30px; display: flex; flex-wrap: wrap; gap: 20px; justify-content: space-between; }}
        .info-card {{ background: white; padding: 15px 25px; border-radius: 15px; box-shadow: 0 2px 5px rgba(0,0,0,0.05); }}
        .filter-bar {{ margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }}
        .filter-btn {{ background: #edf2f7; border: none; padding: 8px 20px; border-radius: 30px; cursor: pointer; font-size: 0.9em; transition: all 0.2s; }}
        .filter-btn.active {{ background: #667eea; color: white; }}
        .source-section {{ margin-bottom: 40px; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 5px 15px rgba(0,0,0,0.05); }}
        .source-title {{ background: #667eea; color: white; padding: 15px 25px; font-size: 1.6em; font-weight: bold; display: flex; align-items: center; gap: 10px; }}
        .results-grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 20px; padding: 20px; }}
        .result-card {{ background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 20px rgba(0,0,0,0.1); transition: transform 0.2s; border: 1px solid #e2e8f0; display: flex; flex-direction: column; }}
        .result-card:hover {{ transform: translateY(-5px); box-shadow: 0 20px 30px rgba(0,0,0,0.15); }}
        .card-media {{ width: 100%; height: 200px; object-fit: cover; background: #f1f5f9; }}
        .video-container {{ position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden; }}
        .video-container iframe {{ position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }}
        .card-content {{ padding: 15px; flex-grow: 1; }}
        .result-title {{ font-size: 1.2em; font-weight: bold; color: #2d3748; margin-bottom: 8px; line-height: 1.4; }}
        .result-link {{ word-break: break-all; margin-bottom: 8px; }}
        .result-link a {{ color: #667eea; text-decoration: none; font-size: 0.85em; }}
        .result-snippet {{ color: #4a5568; font-size: 0.9em; margin: 10px 0; border-right: 3px solid #667eea; padding-right: 10px; }}
        .meta-info {{ display: flex; gap: 15px; margin-top: 10px; font-size: 0.8em; color: #718096; }}
        .badge {{ background: #edf2f7; padding: 4px 10px; border-radius: 20px; display: inline-block; margin-top: 10px; }}
        .footer {{ margin-top: 40px; text-align: center; color: #718096; padding-top: 20px; border-top: 1px solid #e2e8f0; }}
        @media (max-width: 768px) {{ .results-grid {{ grid-template-columns: 1fr; }} }}
    </style>
    <script>
        function filterBySource(source) {{
            const sections = document.querySelectorAll('.source-section');
            const btns = document.querySelectorAll('.filter-btn');
            btns.forEach(btn => btn.classList.remove('active'));
            if (source === 'all') {{
                sections.forEach(section => section.style.display = 'block');
                document.querySelector('.filter-btn[data-source="all"]').classList.add('active');
            }} else {{
                sections.forEach(section => {{
                    if (section.querySelector('.source-title').innerText.includes(source)) {{
                        section.style.display = 'block';
                    }} else {{
                        section.style.display = 'none';
                    }}
                }});
                document.querySelector(`.filter-btn[data-source="${{source}}"]`).classList.add('active');
            }}
        }}
    </script>
</head>
<body>
    <div class="container">
        <h1>🔍 تقرير البحث الشامل</h1>
        <div class="info">
            <div class="info-card"><strong>الاسم المبحوث عنه:</strong> {name}</div>
            <div class="info-card"><strong>التاريخ:</strong> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</div>
            <div class="info-card"><strong>إجمالي النتائج:</strong> {len(self.all_results)}</div>
            <div class="info-card"><strong>المصادر:</strong> {len(grouped)}</div>
        </div>
        <div class="filter-bar">
            <button class="filter-btn active" data-source="all" onclick="filterBySource('all')">الكل</button>
"""
        for source in grouped.keys():
            html += f'            <button class="filter-btn" data-source="{source}" onclick="filterBySource(\'{source}\')">{source}</button>\n'
        html += """
        </div>
"""
        for source, results in grouped.items():
            html += f"""
        <div class="source-section">
            <div class="source-title">📌 {source}</div>
            <div class="results-grid">
"""
            for res in results:
                html += '<div class="result-card">\n'
                if res.get('type') == 'video' and res.get('link') and 'youtube.com' in res['link']:
                    video_id = res['link'].split('v=')[-1].split('&')[0] if 'v=' in res['link'] else res['link'].split('/')[-1]
                    html += f'<div class="video-container"><iframe src="https://www.youtube.com/embed/{video_id}" allowfullscreen></iframe></div>\n'
                elif res.get('thumbnail'):
                    html += f'<img class="card-media" src="{res["thumbnail"]}" alt="صورة" onerror="this.style.display=\'none\'">\n'
                elif res.get('image_url'):
                    html += f'<img class="card-media" src="{res["image_url"]}" alt="صورة" onerror="this.style.display=\'none\'">\n'
                else:
                    html += '<div class="card-media" style="background: #e2e8f0; display: flex; align-items: center; justify-content: center;">🖼️ لا توجد صورة</div>\n'
                html += '<div class="card-content">\n'
                html += f'<div class="result-title">{res.get("title", "بدون عنوان")}</div>\n'
                if 'link' in res and res['link']:
                    html += f'<div class="result-link"><a href="{res["link"]}" target="_blank">{res["link"]}</a></div>\n'
                if 'snippet' in res and res['snippet']:
                    html += f'<div class="result-snippet">{res["snippet"][:300]}</div>\n'
                if res.get('duration'):
                    html += f'<div class="meta-info"><span>⏱️ {res["duration"]}</span></div>\n'
                if res.get('views'):
                    html += f'<div class="meta-info"><span>👁️ {res["views"]}</span></div>\n'
                html += f'<div class="badge">🔎 {res.get("query", "")}</div>\n'
                html += '</div>\n</div>\n'
            html += """
            </div>
        </div>
"""
        html += """
        <div class="footer">
            تم التوليد بواسطة ALKASER ULTIMATE OSINT v13.3 | SerpAPI Integration | تقرير شامل بالصور والفيديوهات
        </div>
    </div>
</body>
</html>
"""
        return html

    # ------------------- البحث الرئيسي -------------------
    def hunt(self, name, proxies_list, stop_event, enable_ocr=False):
        self.proxies_list = proxies_list
        self.log(f"🌐 Starting comprehensive web search for: {name}")
        exact_queries = self.generate_exact_queries(name)
        self.log(f"Using exact queries: {', '.join(exact_queries)}")
        self.all_results = []
        search_engines = [
            ('Google', self.search_google_serpapi),
            ('Bing', self.search_bing_serpapi),
            ('Yandex', self.search_yandex_fallback),
            ('DuckDuckGo', self.search_duckduckgo),
            ('Instagram', self.search_instagram_serpapi),
            ('Twitter', self.search_twitter_serpapi),
            ('Facebook', self.search_facebook_serpapi),
            ('YouTube', self.search_youtube_serpapi),
            ('TikTok', self.search_tiktok_serpapi),
            ('Reddit', self.search_reddit),
            ('Pinterest', self.search_pinterest),
            ('Tumblr', self.search_tumblr),
            ('VK', self.search_vk),
            ('OK.ru', self.search_ok),
            ('News', self.search_news),
            ('Bing Images', self.search_bing_images),
        ]
        total_queries = len(exact_queries) * len(search_engines)
        completed = 0
        for query in exact_queries:
            if stop_event.is_set():
                break
            for engine_name, engine_func in search_engines:
                if stop_event.is_set():
                    break
                try:
                    results = engine_func(query)
                    if results:
                        self.all_results.extend(results)
                        for res in results:
                            if 'link' in res:
                                self.log_found(f"{res['source']}: {res.get('title', '')} - {res['link']}")
                    completed += 1
                    if self.progress:
                        self.progress(int(completed / total_queries * 100))
                except Exception as e:
                    self.log_error(f"Error in {engine_name}: {e}")
        if enable_ocr and PYTESSERACT_AVAILABLE and not stop_event.is_set():
            self.log("Starting OCR on found images...")
            for res in self.all_results:
                if res.get('thumbnail'):
                    try:
                        img_data = safe_request(res['thumbnail'], proxies_list, stream=True)
                        if img_data:
                            img = Image.open(BytesIO(img_data.content))
                            text = pytesseract.image_to_string(img, lang='ara+eng')
                            if name in text:
                                self.log_found(f"OCR found name in image: {res['link']}")
                                res['ocr_text'] = text[:200]
                    except Exception as e:
                        self.log_error(f"OCR error: {e}")
        self.show_stats()
        self.log(f"🌐 Web search completed. Total results: {len(self.all_results)}")
        if self.all_results:
            html_report = self.generate_html_report(name)
            self.log_document(BytesIO(html_report.encode('utf-8')), f"Web_Search_{name}.html", f"🌍 Full web search report for {name}")
        summary = f"🌍 Web Search for '{name}' completed.\nTotal results: {len(self.all_results)}"
        self.log_document(BytesIO(summary.encode('utf-8')), f"Web_Search_{name}.txt", f"Web Search Summary for {name}")

# ======================= واجهة المستخدم الرئيسية =======================
class AlkaserUltimateGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("ALKASER ULTIMATE OSINT - Social Media + Web Search v13.3 (Ultimate)")
        self.root.geometry("1400x900")
        self.root.configure(bg='#0a0a0a')
        self.show_ethical_warning()
        self.proxies_list = load_proxies()
        self.stop_event = threading.Event()
        self.tools_status = self.check_tools()
        self.setup_style()
        self.create_menu()
        self.create_widgets()
        if self.proxies_list:
            threading.Thread(target=refresh_proxy_list, args=(self.proxies_list,), daemon=True).start()

    def show_ethical_warning(self):
        warning = ("⚠️ ETHICAL USE WARNING ⚠️\n\n"
                   "This tool is intended for legitimate security research and authorized investigations only.\n"
                   "Using it to gather information about individuals without their consent may violate privacy laws.\n"
                   "You must ensure you have proper authorization before using this tool.\n\n"
                   "Do you agree to use this tool responsibly and legally?")
        if not messagebox.askyesno("Ethical Warning", warning, icon='warning'):
            self.root.quit()
            sys.exit(0)

    def setup_style(self):
        style = ttk.Style()
        style.theme_use("clam")
        bg_dark = '#0a0a0a'
        bg_light = '#1a1a1a'
        fg_green = '#00ff00'
        style.configure("TNotebook", background=bg_dark, borderwidth=0)
        style.configure("TNotebook.Tab", background=bg_light, foreground=fg_green, padding=[15,5], font=('Consolas',11,'bold'))
        style.map("TNotebook.Tab", background=[("selected", fg_green)], foreground=[("selected", bg_dark)])
        style.configure("TFrame", background=bg_dark)
        style.configure("TButton", background=bg_light, foreground=fg_green, padding=8, font=('Consolas',11,'bold'))
        style.map("TButton", background=[("active", fg_green)], foreground=[("active", bg_dark)])
        style.configure("TEntry", fieldbackground=bg_light, foreground=fg_green, insertcolor=fg_green, font=('Consolas',11))
        style.configure("TLabel", background=bg_dark, foreground=fg_green, font=('Consolas',11))
        style.configure("TProgressbar", background=fg_green, troughcolor=bg_light)
        style.configure("Stop.TButton", background="#ff0000", foreground="#ffffff", font=('Consolas',11,'bold'))
        style.map("Stop.TButton", background=[("active", "#ff5555")])

    def create_menu(self):
        menubar = Menu(self.root, bg='#1a1a1a', fg='#00ff00', activebackground='#00ff00', activeforeground='#0a0a0a')
        file_menu = Menu(menubar, tearoff=0, bg='#1a1a1a', fg='#00ff00')
        file_menu.add_command(label="Load Proxies", command=self.reload_proxies)
        file_menu.add_command(label="Clear Logs", command=self.clear_logs)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        menubar.add_cascade(label="File", menu=file_menu)
        tools_menu = Menu(menubar, tearoff=0, bg='#1a1a1a', fg='#00ff00')
        tools_menu.add_command(label="Check Tools Status", command=self.show_tools_status)
        tools_menu.add_command(label="Open Output Folder", command=self.open_output_folder)
        menubar.add_cascade(label="Tools", menu=tools_menu)
        help_menu = Menu(menubar, tearoff=0, bg='#1a1a1a', fg='#00ff00')
        help_menu.add_command(label="About", command=self.show_about)
        menubar.add_cascade(label="Help", menu=help_menu)
        self.root.config(menu=menubar)

    def open_output_folder(self):
        folder = os.getcwd()
        if sys.platform == 'win32':
            os.startfile(folder)
        else:
            subprocess.Popen(['xdg-open', folder])

    def show_tools_status(self):
        status_text = "Tools & Libraries Status:\n\n"
        for tool, available in self.tools_status.items():
            status_text += f"{tool}: {'✅' if available else '❌'}\n"
        status_text += f"SerpAPI: {'✅ (key loaded)' if SERPAPI_AVAILABLE else '❌ (install serpapi)'}\n"
        messagebox.showinfo("Tools Status", status_text)

    def show_about(self):
        about_text = """
ALKASER ULTIMATE OSINT
Version 13.3 - Ultimate Social & Web Search

Features:
- Social Media Hunter (60+ sites) with Instagram scraping
- Web Search Module: Google, Bing, Yandex, DuckDuckGo, plus social networks via SerpAPI
- **SerpAPI Integration** for rich media posts (images, videos, full text)
- Exact name search (no partial splitting)
- Beautiful HTML report with filters, images, and embedded videos
- OCR support
- Telegram integration
- Proxy support

SerpAPI Key: Included (free tier)
Ethical Use Only!
"""
        messagebox.showinfo("About", about_text)

    def check_tools(self):
        status = {}
        status['PIL'] = PIL_AVAILABLE
        status['colorama'] = COLORAMA_AVAILABLE
        status['instagrapi'] = INSTAGRAPI_AVAILABLE
        status['pytesseract'] = PYTESSERACT_AVAILABLE
        status['googlesearch'] = GOOGLESEARCH_AVAILABLE
        return status

    def create_widgets(self):
        top_frame = ttk.Frame(self.root)
        top_frame.pack(fill='x', padx=10, pady=5)
        proxy_frame = ttk.LabelFrame(top_frame, text="Proxy Status", padding=5)
        proxy_frame.pack(side='left', padx=5)
        self.proxy_label = ttk.Label(proxy_frame, text=f"Loaded: {len(self.proxies_list)}", font=('Consolas',10))
        self.proxy_label.pack()
        self.proxy_working_label = ttk.Label(proxy_frame, text=f"Working: {len(proxy_cache['working'])}", font=('Consolas',10))
        self.proxy_working_label.pack()
        ttk.Button(proxy_frame, text="Reload", command=self.reload_proxies).pack(pady=2)
        stop_btn = ttk.Button(top_frame, text="⏹️ STOP ALL", command=self.stop_all, style="Stop.TButton")
        stop_btn.pack(side='right', padx=5)
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill='both', expand=True, padx=10, pady=5)
        self.create_tab_social_hunt()
        self.create_tab_web_search()
        self.create_tab_about()
        log_frame = ttk.LabelFrame(self.root, text="📋 Live Logs & Errors", padding=5)
        log_frame.pack(fill='both', expand=True, padx=10, pady=5)
        log_toolbar = ttk.Frame(log_frame)
        log_toolbar.pack(fill='x')
        ttk.Button(log_toolbar, text="Clear Logs", command=self.clear_logs).pack(side='left', padx=2)
        ttk.Button(log_toolbar, text="Save Logs", command=self.save_logs).pack(side='left', padx=2)
        ttk.Button(log_toolbar, text="Copy All", command=self.copy_logs).pack(side='left', padx=2)
        self.log_text = scrolledtext.ScrolledText(log_frame, height=10, bg='#1a1a1a', fg='#00ff00', insertbackground='#00ff00', font=('Consolas', 10))
        self.log_text.pack(fill='both', expand=True)
        self.log_text.tag_configure("ERROR", foreground="#ff5555")
        self.log_text.tag_configure("INFO", foreground="#00ff00")
        self.log_text.tag_configure("FOUND", foreground="#ffff00")
        self.log_text.tag_configure("WARNING", foreground="#ffaa00")

    def clear_logs(self):
        self.log_text.delete(1.0, tk.END)

    def save_logs(self):
        filename = filedialog.asksaveasfilename(defaultextension=".txt", filetypes=[("Text files", "*.txt")])
        if filename:
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(self.log_text.get(1.0, tk.END))

    def copy_logs(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.log_text.get(1.0, tk.END))

    def log_to_panel(self, text, level="INFO"):
        timestamp = datetime.now().strftime("%H:%M:%S")
        tag = level
        self.log_text.insert(tk.END, f"[{timestamp}] [{level}] {text}\n", tag)
        self.log_text.see(tk.END)

    def reload_proxies(self):
        self.proxies_list = load_proxies()
        threading.Thread(target=self._refresh_proxies_background, daemon=True).start()
        self.log_to_panel(f"Loaded {len(self.proxies_list)} proxies. Testing in background...", "INFO")

    def _refresh_proxies_background(self):
        refresh_proxy_list(self.proxies_list)
        self.root.after(0, self.update_proxy_display)

    def update_proxy_display(self):
        with proxy_lock:
            working = len(proxy_cache["working"])
        self.proxy_working_label.config(text=f"Working: {working}")
        self.log_to_panel(f"Proxy test completed. Working: {working}", "INFO")

    def stop_all(self):
        self.stop_event.set()
        self.log_to_panel("STOP signal sent. Operations will stop after current tasks.", "WARNING")

    def run_module(self, target_func, *args, **kwargs):
        self.stop_event.clear()
        thread = threading.Thread(target=target_func, args=args, kwargs=kwargs, daemon=True)
        thread.start()

    def create_tab_social_hunt(self):
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="🌐 Social Hunter")
        options_frame = ttk.LabelFrame(tab, text="Instagram Options", padding=5)
        options_frame.pack(fill='x', padx=10, pady=5)
        self.use_instagrapi_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(options_frame, text="Use instagrapi (advanced Instagram scraping)", variable=self.use_instagrapi_var).pack(side='left', padx=5)
        ttk.Label(options_frame, text="Username:").pack(side='left', padx=5)
        self.instagram_user_entry = ttk.Entry(options_frame, width=20)
        self.instagram_user_entry.pack(side='left', padx=5)
        ttk.Label(options_frame, text="Password:").pack(side='left', padx=5)
        self.instagram_pass_entry = ttk.Entry(options_frame, width=20, show="*")
        self.instagram_pass_entry.pack(side='left', padx=5)
        input_frame = ttk.Frame(tab)
        input_frame.pack(fill='x', padx=10, pady=10)
        ttk.Label(input_frame, text="Username to search:", font=('Consolas',12)).pack(side='left')
        self.social_entry = ttk.Entry(input_frame, width=50)
        self.social_entry.pack(side='left', padx=5)
        self.social_entry.insert(0, "Enter username (e.g., john_doe)")
        self.social_entry.bind("<FocusIn>", lambda e: self.social_entry.delete(0, tk.END) if self.social_entry.get() == "Enter username (e.g., john_doe)" else None)
        self.social_btn = ttk.Button(input_frame, text="Start Hunt", command=self.run_social_hunt)
        self.social_btn.pack(side='left')
        self.social_progress = ttk.Progressbar(tab, orient='horizontal', length=400, mode='determinate')
        self.social_progress.pack(pady=5)
        self.social_output = scrolledtext.ScrolledText(tab, height=15, bg='#1a1a1a', fg='#00ff00', insertbackground='#00ff00', font=('Consolas', 11))
        self.social_output.tag_configure("error", foreground="#ff5555")
        self.social_output.pack(fill='both', expand=True, padx=10, pady=5)

    def run_social_hunt(self):
        username = self.social_entry.get().strip()
        if not username or username == "Enter username (e.g., john_doe)":
            messagebox.showwarning("Input Error", "Please enter a username to search.")
            return
        use_instagrapi = self.use_instagrapi_var.get()
        insta_user = self.instagram_user_entry.get().strip()
        insta_pass = self.instagram_pass_entry.get().strip()
        self.social_btn.config(state='disabled')
        self.social_progress['value'] = 0
        self.run_module(self._social_hunt_thread, username, use_instagrapi, insta_user, insta_pass)

    def _social_hunt_thread(self, username, use_instagrapi, insta_user, insta_pass):
        def update_progress(val):
            self.social_progress['value'] = val
        module = SocialMediaHunter(self.social_output, self.log_text, update_progress, use_instagrapi=use_instagrapi, instagram_username=insta_user, instagram_password=insta_pass)
        self.log_to_panel(f"Starting social media hunt for: {username}", "INFO")
        send_telegram_message(f"🔍 Starting social media hunt for: {username}")
        module.hunt(username, self.proxies_list, self.stop_event)
        self.root.after(0, self._social_hunt_done)

    def _social_hunt_done(self):
        self.social_btn.config(state='normal')
        self.social_progress['value'] = 100
        self.log_to_panel("Social media hunt completed.", "INFO")
        send_telegram_message("✅ Social media hunt completed.")

    def create_tab_web_search(self):
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="🌍 Web Search")
        options_frame = ttk.LabelFrame(tab, text="Search Options", padding=5)
        options_frame.pack(fill='x', padx=10, pady=5)
        self.enable_ocr_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(options_frame, text="Enable OCR on images (requires pytesseract)", variable=self.enable_ocr_var).pack(side='left', padx=5)
        input_frame = ttk.Frame(tab)
        input_frame.pack(fill='x', padx=10, pady=10)
        ttk.Label(input_frame, text="Name to search:", font=('Consolas',12)).pack(side='left')
        self.web_entry = ttk.Entry(input_frame, width=50)
        self.web_entry.pack(side='left', padx=5)
        self.web_entry.insert(0, "Enter full name (e.g., Ahmed Mohamed)")
        self.web_entry.bind("<FocusIn>", lambda e: self.web_entry.delete(0, tk.END) if self.web_entry.get() == "Enter full name (e.g., Ahmed Mohamed)" else None)
        self.web_btn = ttk.Button(input_frame, text="Start Search", command=self.run_web_search)
        self.web_btn.pack(side='left')
        self.web_progress = ttk.Progressbar(tab, orient='horizontal', length=400, mode='determinate')
        self.web_progress.pack(pady=5)
        self.web_output = scrolledtext.ScrolledText(tab, height=15, bg='#1a1a1a', fg='#00ff00', insertbackground='#00ff00', font=('Consolas', 11))
        self.web_output.tag_configure("error", foreground="#ff5555")
        self.web_output.pack(fill='both', expand=True, padx=10, pady=5)

    def run_web_search(self):
        name = self.web_entry.get().strip()
        if not name or name == "Enter full name (e.g., Ahmed Mohamed)":
            messagebox.showwarning("Input Error", "Please enter a name to search.")
            return
        enable_ocr = self.enable_ocr_var.get()
        self.web_btn.config(state='disabled')
        self.web_progress['value'] = 0
        self.run_module(self._web_search_thread, name, enable_ocr)

    def _web_search_thread(self, name, enable_ocr):
        def update_progress(val):
            self.web_progress['value'] = val
        module = WebSearchModule(self.web_output, self.log_text, update_progress)
        self.log_to_panel(f"Starting comprehensive web search for: {name}", "INFO")
        send_telegram_message(f"🌍 Starting comprehensive web search for: {name}")
        module.hunt(name, self.proxies_list, self.stop_event, enable_ocr=enable_ocr)
        self.root.after(0, self._web_search_done)

    def _web_search_done(self):
        self.web_btn.config(state='normal')
        self.web_progress['value'] = 100
        self.log_to_panel("Web search completed.", "INFO")
        send_telegram_message("✅ Web search completed.")

    def create_tab_about(self):
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="ℹ️ About")
        about_text = """
╔══════════════════════════════════════╗
║   ALKASER ULTIMATE - OSINT Suite    ║
╚══════════════════════════════════════╝

🌐 Social Hunter (60+ sites)
  • Multi-threaded search
  • Instagram: advanced data via instagrapi
  • Profile info, posts, followers
  • Full extraction of profile pictures and recent posts

🌍 Web Search Module (Ultra-Precise Exact Match)
  • **SerpAPI Integration** for Google, Bing, Instagram, Twitter, Facebook, YouTube, TikTok
  • Fallback methods if SerpAPI fails
  • Rich media posts: images, videos, full text
  • OCR on images
  • Beautiful HTML report with filters, embedded videos

📤 Telegram Integration
  • All results sent to Telegram
  • Photos and documents

🛡️ Ethical Use Only!
        """
        label = tk.Text(tab, bg='#0a0a0a', fg='#00ff00', wrap='word', font=('Consolas',11), borderwidth=0)
        label.pack(fill='both', expand=True, padx=20, pady=20)
        label.insert('1.0', about_text)
        label.config(state='disabled')

if __name__ == "__main__":
    root = tk.Tk()
    app = AlkaserUltimateGUI(root)
    root.mainloop()
