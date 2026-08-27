@echo off
setlocal
cd /d "%~dp0.."
python -m agent_backend.server
