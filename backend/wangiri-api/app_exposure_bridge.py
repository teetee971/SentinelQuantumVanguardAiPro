from app_redis import app
from collective_exposure_user import create_user_exposure_router


app.include_router(create_user_exposure_router())
