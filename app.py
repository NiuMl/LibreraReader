from flask import Flask, jsonify, request, send_file, render_template, session, redirect, url_for
from flask_cors import CORS
import os
import sys
from pathlib import Path
import sqlite3
import hashlib
import uuid
from datetime import datetime, timedelta

from epub_parser import EpubParser

app = Flask(__name__)
app.secret_key = 'nreader_secret_key'
CORS(app)

NOVELS_DIR = Path('D:/temp')
DB_PATH = 'novels.db'

tokens = {}

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute('DROP TABLE IF EXISTS novels')
    
    cursor.execute('''
        CREATE TABLE novels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            author TEXT DEFAULT '本地文件',
            file_path TEXT NOT NULL,
            cover TEXT DEFAULT '',
            category TEXT DEFAULT ''
        )
    ''')
    
    if NOVELS_DIR.exists():
        for file in NOVELS_DIR.glob('*.txt'):
            file_path_str = str(file).replace("\\", "/")
            cursor.execute('''
                INSERT INTO novels (title, file_path, category)
                VALUES (?, ?, ?)
            ''', (file.stem, file_path_str, '未分类'))
        
        for file in NOVELS_DIR.glob('*.epub'):
            file_path_str = str(file).replace("\\", "/")
            try:
                parser = EpubParser(file_path_str)
                title, author, _, _, _ = parser.parse()
                if not author:
                    author = '本地文件'
                cursor.execute('''
                    INSERT INTO novels (title, author, file_path, category)
                    VALUES (?, ?, ?, ?)
                ''', (title, author, file_path_str, '未分类'))
            except Exception as e:
                cursor.execute('''
                    INSERT INTO novels (title, file_path, category)
                    VALUES (?, ?, ?)
                ''', (file.stem, file_path_str, '未分类'))
        
        for category_dir in NOVELS_DIR.iterdir():
            if category_dir.is_dir():
                category_name = category_dir.name
                for file in category_dir.glob('*.txt'):
                    file_path_str = str(file).replace("\\", "/")
                    cursor.execute('''
                        INSERT INTO novels (title, file_path, category)
                        VALUES (?, ?, ?)
                    ''', (file.stem, file_path_str, category_name))
                
                for file in category_dir.glob('*.epub'):
                    file_path_str = str(file).replace("\\", "/")
                    try:
                        parser = EpubParser(file_path_str)
                        title, author, _, _, _ = parser.parse()
                        if not author:
                            author = '本地文件'
                        cursor.execute('''
                            INSERT INTO novels (title, author, file_path, category)
                            VALUES (?, ?, ?, ?)
                        ''', (title, author, file_path_str, category_name))
                    except Exception as e:
                        cursor.execute('''
                            INSERT INTO novels (title, file_path, category)
                            VALUES (?, ?, ?)
                        ''', (file.stem, file_path_str, category_name))
    
    cursor.execute('''CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT NOT NULL UNIQUE,
        password TEXT NOT NULL
    )''')
    
    cursor.execute('SELECT COUNT(*) FROM users')
    count = cursor.fetchone()[0]
    if count == 0:
        default_password = hashlib.md5('123456'.encode()).hexdigest()
        cursor.execute('INSERT INTO users (username, password) VALUES (?, ?)', ('admin', default_password))
    
    conn.commit()
    conn.close()

def get_categories_from_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute('''
        SELECT DISTINCT category 
        FROM novels 
        WHERE category != '' 
        ORDER BY category
    ''')
    
    categories = cursor.fetchall()
    conn.close()
    
    return [{'id': category[0], 'name': category[0]} for category in categories]

def get_novels_from_db(page=1, page_size=10, search='', category=''):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    offset = (page - 1) * page_size
    
    if search and category:
        cursor.execute('''
            SELECT id, title, author, file_path, cover, category 
            FROM novels 
            WHERE title LIKE ? AND category = ?
            ORDER BY id 
            LIMIT ? OFFSET ?
        ''', (f'%{search}%', category, page_size, offset))
        novels = cursor.fetchall()
        cursor.execute('SELECT COUNT(*) FROM novels WHERE title LIKE ? AND category = ?', (f'%{search}%', category))
    elif search:
        cursor.execute('''
            SELECT id, title, author, file_path, cover, category 
            FROM novels 
            WHERE title LIKE ? 
            ORDER BY id 
            LIMIT ? OFFSET ?
        ''', (f'%{search}%', page_size, offset))
        novels = cursor.fetchall()
        cursor.execute('SELECT COUNT(*) FROM novels WHERE title LIKE ?', (f'%{search}%',))
    elif category:
        cursor.execute('''
            SELECT id, title, author, file_path, cover, category 
            FROM novels 
            WHERE category = ?
            ORDER BY id 
            LIMIT ? OFFSET ?
        ''', (category, page_size, offset))
        novels = cursor.fetchall()
        cursor.execute('SELECT COUNT(*) FROM novels WHERE category = ?', (category,))
    else:
        cursor.execute('''
            SELECT id, title, author, file_path, cover, category 
            FROM novels 
            ORDER BY id 
            LIMIT ? OFFSET ?
        ''', (page_size, offset))
        novels = cursor.fetchall()
        cursor.execute('SELECT COUNT(*) FROM novels')
    
    count_result = cursor.fetchone()
    total = count_result[0] if count_result else 0
    
    conn.close()
    
    return [{
        'id': str(novel[0]),
        'title': novel[1],
        'author': novel[2],
        'cover': novel[4],
        'isInShelf': False,
        'filePath': f'file:///{novel[3]}',
        'category': novel[5]
    } for novel in novels], total

def validate_token(token):
    if token not in tokens:
        return False
    
    expiry_time = tokens[token]
    if datetime.now() > expiry_time:
        del tokens[token]
        return False
    
    return True

def generate_token():
    token = str(uuid.uuid4())
    expiry_time = datetime.now() + timedelta(days=1)
    tokens[token] = expiry_time
    return token, expiry_time

@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if not username or not password:
        return jsonify({'code': 1, 'message': '用户名和密码不能为空'}), 400
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    hashed_password = hashlib.md5(password.encode()).hexdigest()
    cursor.execute('SELECT * FROM users WHERE username = ? AND password = ?', (username, hashed_password))
    user = cursor.fetchone()
    
    conn.close()
    
    if user:
        token, expiry = generate_token()
        return jsonify({
            'code': 0,
            'message': '登录成功',
            'token': token,
            'expiry': expiry.strftime('%Y-%m-%d %H:%M:%S')
        })
    else:
        return jsonify({'code': 1, 'message': '用户名或密码错误'}), 401

@app.route('/api/categories', methods=['GET'])
def get_categories_list():
    token = request.headers.get('Authorization')
    if not token or not validate_token(token):
        return jsonify({'code': 1, 'message': '未授权或token已过期'}), 401
    
    categories = get_categories_from_db()
    
    return jsonify({
        'code': 0,
        'categories': categories
    })

@app.route('/api/novels', methods=['GET'])
def get_novels_list():
    token = request.headers.get('Authorization')
    if not token or not validate_token(token):
        return jsonify({'code': 1, 'message': '未授权或token已过期'}), 401
    
    page = int(request.args.get('page', 1))
    page_size = int(request.args.get('page_size', 10))
    search = request.args.get('search', '')
    category = request.args.get('category', '')
    
    novels, total = get_novels_from_db(page, page_size, search, category)
    
    return jsonify({
        'novels': novels,
        'total': total,
        'page': page,
        'page_size': page_size
    })

@app.route('/api/novel/<int:novel_id>', methods=['GET'])
def get_novel_content(novel_id):
    token = request.headers.get('Authorization')
    if not token or not validate_token(token):
        return jsonify({'code': 1, 'message': '未授权或token已过期'}), 401
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute('SELECT title, file_path FROM novels WHERE id = ?', (novel_id,))
    result = cursor.fetchone()
    
    conn.close()
    
    if not result:
        return jsonify({'error': 'Novel not found'}), 404
    
    title, file_path = result
    file_path = Path(file_path)
    
    if not file_path.exists():
        return jsonify({'error': 'File not found'}), 404
    
    try:
        if file_path.suffix.lower() == '.epub':
            parser = EpubParser(str(file_path))
            epub_title, author, chapters, total_words, cover_data = parser.parse()
            
            content = '\n\n'.join(chapter['content'] for chapter in chapters)
            
            return jsonify({
                'id': str(novel_id),
                'title': epub_title if epub_title else title,
                'author': author if author else '',
                'content': content,
                'chapters': chapters
            })
        else:
            content = read_file_with_encoding(file_path)
            
            return jsonify({
                'id': str(novel_id),
                'title': title,
                'content': content
            })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/download/<int:novel_id>', methods=['GET'])
def download_novel(novel_id):
    token = request.headers.get('Authorization')
    if not token or not validate_token(token):
        return jsonify({'code': 1, 'message': '未授权或token已过期'}), 401
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute('SELECT title, file_path FROM novels WHERE id = ?', (novel_id,))
    result = cursor.fetchone()
    
    conn.close()
    
    if not result:
        return jsonify({'error': 'Novel not found'}), 404
    
    title, file_path = result
    file_path = Path(file_path)
    
    if not file_path.exists():
        return jsonify({'error': 'File not found'}), 404
    
    try:
        return send_file(
            str(file_path),
            as_attachment=True,
            download_name=file_path.name
        )
    except Exception as e:
        return jsonify({'error': str(e)}), 500

def read_file_with_encoding(file_path):
    encodings = ['utf-8', 'gbk', 'gb2312', 'gb18030', 'cp1252']
    
    for encoding in encodings:
        try:
            with open(file_path, 'r', encoding=encoding) as f:
                return f.read()
        except UnicodeDecodeError:
            continue
    
    raise ValueError(f"Unable to decode file with any of the following encodings: {encodings}")

@app.route('/api/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok', 'message': 'NReader Backend is running'})

# Web 管理页面路由
@app.route('/', methods=['GET'])
def index():
    if 'username' not in session:
        return redirect(url_for('login_page'))
    return redirect(url_for('dashboard'))

@app.route('/login', methods=['GET', 'POST'])
def login_page():
    if request.method == 'GET':
        if 'username' in session:
            return redirect(url_for('dashboard'))
        return render_template('login.html')
    
    # POST - 处理登录
    username = request.form.get('username')
    password = request.form.get('password')
    
    if not username or not password:
        return render_template('login.html', error='请填写用户名和密码')
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    hashed_password = hashlib.md5(password.encode()).hexdigest()
    cursor.execute('SELECT * FROM users WHERE username = ? AND password = ?', 
                   (username, hashed_password))
    user = cursor.fetchone()
    conn.close()
    
    if user:
        session['username'] = username
        session['user_id'] = user[0]
        return redirect(url_for('dashboard'))
    else:
        return render_template('login.html', error='用户名或密码错误')

@app.route('/dashboard', methods=['GET'])
def dashboard():
    if 'username' not in session:
        return redirect(url_for('login_page'))
    
    # 获取小说列表
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT id, title, author, file_path FROM novels ORDER BY id')
    novels = cursor.fetchall()
    
    novels_list = []
    for novel in novels:
        novels_list.append({
            'id': novel[0],
            'title': novel[1],
            'author': novel[2],
            'file_path': novel[3]
        })
    
    # 获取用户列表
    cursor.execute('SELECT id, username FROM users ORDER BY id')
    users = cursor.fetchall()
    conn.close()
    
    users_list = []
    for user in users:
        users_list.append({
            'id': user[0],
            'username': user[1]
        })
    
    return render_template('dashboard.html', 
                          username=session['username'],
                          novels=novels_list,
                          users=users_list)

@app.route('/logout', methods=['GET'])
def logout():
    session.clear()
    return redirect(url_for('login_page'))

# 用户管理 API
@app.route('/users/add', methods=['POST'])
def add_user():
    if 'username' not in session:
        return redirect(url_for('login_page'))
    
    username = request.form.get('username')
    password = request.form.get('password')
    
    if not username or not password:
        return redirect(url_for('dashboard'))
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    try:
        hashed_password = hashlib.md5(password.encode()).hexdigest()
        cursor.execute('INSERT INTO users (username, password) VALUES (?, ?)', 
                      (username, hashed_password))
        conn.commit()
    except sqlite3.IntegrityError:
        pass  # 用户名已存在
    
    conn.close()
    return redirect(url_for('dashboard'))

@app.route('/users/edit', methods=['POST'])
def edit_user():
    if 'username' not in session:
        return redirect(url_for('login_page'))
    
    user_id = request.form.get('user_id')
    username = request.form.get('username')
    password = request.form.get('password')
    
    if not user_id or not username:
        return redirect(url_for('dashboard'))
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    if password:
        hashed_password = hashlib.md5(password.encode()).hexdigest()
        cursor.execute('UPDATE users SET username = ?, password = ? WHERE id = ?', 
                      (username, hashed_password, user_id))
    else:
        cursor.execute('UPDATE users SET username = ? WHERE id = ?', 
                      (username, user_id))
    
    conn.commit()
    conn.close()
    return redirect(url_for('dashboard'))

@app.route('/users/delete/<int:user_id>', methods=['POST'])
def delete_user(user_id):
    if 'username' not in session:
        return redirect(url_for('login_page'))
    
    # 不允许删除 admin 用户
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT username FROM users WHERE id = ?', (user_id,))
    user = cursor.fetchone()
    
    if user and user[0] != 'admin':
        cursor.execute('DELETE FROM users WHERE id = ?', (user_id,))
        conn.commit()
    
    conn.close()
    return redirect(url_for('dashboard'))

if __name__ == '__main__':
    print('NReader Backend Server')
    print(f'Novels directory: {NOVELS_DIR}')
    
    init_db()
    print('Database initialized')
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('SELECT COUNT(*) FROM novels')
    count = cursor.fetchone()[0]
    conn.close()
    
    print(f'Found {count} novels')
    print('Server starting on http://localhost:5000')
    app.run(host='0.0.0.0', port=5000, debug=True)