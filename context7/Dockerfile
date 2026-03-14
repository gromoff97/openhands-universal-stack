FROM node:22-slim

RUN npm install -g @upstash/context7-mcp@2.1.4

EXPOSE 3000

ENTRYPOINT ["context7-mcp"]
CMD ["--transport", "http", "--port", "3000"]
