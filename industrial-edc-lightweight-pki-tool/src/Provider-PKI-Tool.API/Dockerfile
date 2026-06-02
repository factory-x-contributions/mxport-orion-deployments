FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS base
USER $APP_UID
WORKDIR /app
EXPOSE 8080
EXPOSE 8081

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src
COPY ["Provider-PKI-Tool.API/Provider-PKI-Tool.API.csproj", "Provider-PKI-Tool.API/"]
RUN dotnet restore "Provider-PKI-Tool.API/Provider-PKI-Tool.API.csproj"
COPY . .
WORKDIR "/src/Provider-PKI-Tool.API"
RUN dotnet build "./Provider-PKI-Tool.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "./Provider-PKI-Tool.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Provider-PKI-Tool.API.dll"]
