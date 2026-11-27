"""让 tests/ 下的用例可以直接 `from app... import ...`。

pytest 默认只把测试文件所在目录加入 sys.path，而本项目是"包在子目录里、
入口在上一层"的结构（`llm-service/main.py` + `llm-service/app/`）。
在这里显式把 llm-service 加入 sys.path，避免测试代码里到处写 sys.path hack。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
