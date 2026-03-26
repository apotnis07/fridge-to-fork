const COGNITO_DOMAIN = "https://us-east-1e8orxn70t.auth.us-east-1.amazoncognito.com";
const CLIENT_ID = "2bcr0f7nd14rv75lh7colsakif";
const REDIRECT_URI = "http://localhost:8080/callback.html";
const SCOPES = "openid email profile";

function login() {
    const authUrl = `${COGNITO_DOMAIN}/oauth2/authorize`
        + `?response_type=code`
        + `&client_id=${CLIENT_ID}`
        + `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}`
        + `&scope=${encodeURIComponent(SCOPES)}`;
    window.location.href = authUrl;
}

function logout() {
    sessionStorage.clear();
    const logoutUrl = `${COGNITO_DOMAIN}/logout`
        + `?client_id=${CLIENT_ID}`
        + `&logout_uri=${encodeURIComponent("http://localhost:8080/index.html")}`;
    window.location.href = logoutUrl;
}

// Use this for every API call instead of plain fetch()
async function apiFetch(url, options = {}) {
    const token = sessionStorage.getItem("access_token");

    if (!token) {
        login(); // token missing, bounce to login
        return;
    }

    const response = await fetch(url, {
        ...options,
        headers: {
            ...options.headers,
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    });

    if (response.status === 401) {
        // Token expired or invalid
        sessionStorage.clear();
        login();
        return;
    }

    return response;
} 

// Call this on any page that requires login
function requireAuth() {
    const token = sessionStorage.getItem("access_token");
    if (!token) {
        login();
    }
}

