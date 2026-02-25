# OIDC Setup Guide

Streampack supports "Sign in with Google" and "Sign in with GitHub" via OpenID Connect / OAuth2.
Both providers are free to set up.
OIDC is optional - the app works with OTP-only authentication when no provider credentials are configured.

## Google

### 1. Create a project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or select an existing one)
3. No billing is required for OAuth2 client credentials

### 2. Configure the OAuth consent screen

1. Navigate to **APIs & Services > OAuth consent screen**
2. Choose **External** user type
3. Fill in the required fields:
   - App name: your site name (e.g., "bytecode.news")
   - User support email: your email
   - Developer contact email: your email
4. Add scopes: `openid`, `email`, `profile`
5. Save

### 3. Create credentials

1. Navigate to **APIs & Services > Credentials**
2. Click **Create Credentials > OAuth client ID**
3. Application type: **Web application**
4. Name: your site name
5. Authorized redirect URIs: `https://rest.your-domain.com/login/oauth2/code/google`
   - The redirect URI must point to the **API** domain, not the frontend
   - For local development: `http://localhost:8080/login/oauth2/code/google`
6. Copy the **Client ID** and **Client Secret**

### 4. Configure Streampack

Set the environment variables:

```bash
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-client-secret"
```

Or add them to your `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: your-client-id
            client-secret: your-client-secret
```

## GitHub

### 1. Create an OAuth App

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **OAuth Apps > New OAuth App**
3. Fill in:
   - Application name: your site name
   - Homepage URL: `https://your-domain.com`
   - Authorization callback URL: `https://rest.your-domain.com/login/oauth2/code/github`
   - The callback URL must point to the **API** domain, not the frontend
   - For local development: `http://localhost:8080/login/oauth2/code/github`
4. Click **Register application**
5. Copy the **Client ID**
6. Generate a **Client Secret** and copy it

### 2. Configure Streampack

Set the environment variables:

```bash
export GITHUB_CLIENT_ID="your-client-id"
export GITHUB_CLIENT_SECRET="your-client-secret"
```

Or add them to your `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: your-client-id
            client-secret: your-client-secret
```

## How It Works

When a user clicks "Sign in with Google" or "Sign in with GitHub":

1. The frontend redirects to `/oauth2/authorization/google` (or `github`)
2. Spring Security redirects the user to the provider's login page
3. After the user authenticates, the provider redirects back to `/login/oauth2/code/{provider}`
4. Spring Security exchanges the authorization code for user info
5. The app's `OidcAuthenticationSuccessHandler` extracts the email and display name
6. Identity convergence runs: find-or-create user by email, ensure HTTP ServiceBinding, issue JWT
7. The browser is redirected to `{frontendUrl}/auth/callback#token=<jwt>`
8. The frontend extracts the JWT from the URL fragment and stores it

## Split-Domain Deployment

When the API and frontend live on different domains (e.g. `rest.bytecode.news` and `bytecode.news`), two properties control the redirect flow:

- `streampack.baseUrl` - the API's public URL (`https://rest.bytecode.news`).
  This is what Google/GitHub call back to.
- `streampack.frontendUrl` - the frontend's public URL (`https://bytecode.news`).
  This is where the success handler redirects the user after authentication.

If `frontendUrl` is not set, it defaults to `baseUrl` (single-domain deployment).

```yaml
streampack:
  base-url: https://rest.bytecode.news
  frontend-url: https://bytecode.news
```

The CORS configuration must include all frontend origins that call the API:

```bash
export CORS_ORIGINS="https://bytecode.news,https://nextjs.bytecode.news,https://svelte.bytecode.news,http://localhost:3000"
```

## Notes

- Both providers are optional and independent - you can configure one, both, or neither
- When neither is configured, the OIDC endpoints are not registered
- The `scope` configuration in `application.yml` requests `openid,email,profile` for Google and `user:email` for GitHub
- Email is the identity anchor: if a user signs in with Google and later signs in with GitHub using the same email, they get the same account
- No billing is required for either provider's OAuth2 service
