import { Amplify } from 'https://esm.sh/aws-amplify@6';
import { fetchAuthSession, getCurrentUser, signOut } from 'https://esm.sh/aws-amplify@6/auth';


Amplify.configure({
    Auth: {
        Cognito: {
            userPoolId: 'us-east-1_E8orXN70T',
            userPoolClientId: '2bcr0f7nd14rv75lh7colsakif',
            loginWith: { email: true }
        }
    }
});

// Get JWT token for API calls
export async function getToken() {
    const session = await fetchAuthSession();
    return session.tokens.accessToken.toString();
}

// Redirect to login if not authenticated
export async function requireAuth() {
    try {
        await getCurrentUser();
    } catch {
        window.location.href = '/login.html';
    }
}

// Sign out and redirect to home
export async function logout() {
    await signOut();
    window.location.href = '/index.html';
}

// Authenticated fetch — attaches JWT automatically
export async function apiFetch(url, options = {}) {
    const token = await getToken();
    return fetch(url, {
        ...options,
        headers: {
            ...options.headers,
            'Authorization': `Bearer ${token}`,
            'Content-Type': options.headers?.['Content-Type'] || 'application/json'
        }
    });
}