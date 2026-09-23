FROM python:3.13-alpine
WORKDIR /app
COPY index.html server.py test_app.py ./
RUN python -m unittest -v
ENV PYTHONUNBUFFERED=1
CMD ["python","server.py"]
