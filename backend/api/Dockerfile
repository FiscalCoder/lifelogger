FROM node:20-alpine
WORKDIR /app
RUN apk add --no-cache ca-certificates curl \
    && curl -fsS https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
      -o /etc/ssl/certs/aws-rds-global-bundle.pem
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/certs/aws-rds-global-bundle.pem
COPY package.json .
RUN npm install
COPY . .
RUN npm run build
EXPOSE 8000
CMD ["node", "dist/index.js"]
