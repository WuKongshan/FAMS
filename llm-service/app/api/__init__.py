"""HTTP 层：只放路由定义，业务逻辑一律下沉到 services。"""

from app.api.routes import router

__all__ = ["router"]
