# ============ 麦乐迪前端：Node 构建 → nginx 托管 ============
FROM node:20-alpine AS build
WORKDIR /build

# 构建期注入 API 地址：容器内走 nginx 同源反代（/api、/agent、/img）
ARG VITE_API_BASE=/api
ARG VITE_AGENT_BASE=/agent
ENV VITE_API_BASE=$VITE_API_BASE \
    VITE_AGENT_BASE=$VITE_AGENT_BASE

# npm 用国内镜像加速
RUN npm config set registry https://registry.npmmirror.com

# 先拷 package 文件装依赖（层缓存）
COPY package.json package-lock.json* ./
RUN npm ci || npm install

COPY . .
RUN npm run build

# 运行阶段
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
