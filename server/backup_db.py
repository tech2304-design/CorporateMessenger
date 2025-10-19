#!/usr/bin/env python3
import shutil, os
from datetime import datetime
DB_PATH = 'data/messenger.db'
BACKUP_DIR = 'backup'
os.makedirs(BACKUP_DIR, exist_ok=True)
now = datetime.utcnow().strftime('%Y%m%dT%H%M%SZ')
backup_file = os.path.join(BACKUP_DIR, f'messenger_{now}.db')
shutil.copy2(DB_PATH, backup_file)
print('Backup created:', backup_file)
