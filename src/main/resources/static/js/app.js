// Global App Configuration and Utilities

// Theme Management
const THEME_STORAGE_KEY = 'enterprise-ai-theme';

function initializeTheme() {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const isDark = savedTheme ? savedTheme === 'dark' : prefersDark;

    if (isDark) {
        document.body.classList.add('dark-mode');
        updateDarkModeCheckbox(true);
    }
}

function toggleTheme() {
    document.body.classList.toggle('dark-mode');
    const isDark = document.body.classList.contains('dark-mode');
    localStorage.setItem(THEME_STORAGE_KEY, isDark ? 'dark' : 'light');
    updateDarkModeCheckbox(isDark);
}

function updateDarkModeCheckbox(isDark) {
    const checkbox = document.getElementById('darkMode');
    if (checkbox) {
        checkbox.checked = isDark;
    }
}

// HTMX Configuration
document.addEventListener('htmx:configRequest', function(event) {
    event.detail.headers['X-CSRF-Token'] = getCsrfToken();
});

// Handle HTMX errors
document.addEventListener('htmx:responseError', function(event) {
    console.error('HTMX Error:', event.detail);
    showErrorNotification('An error occurred. Please try again.');
});

// Handle HTMX swap errors
document.addEventListener('htmx:swapError', function(event) {
    console.error('Swap Error:', event.detail);
    showErrorNotification('Failed to update page content.');
});

// Notification System
function showNotification(message, type = 'info', duration = 3000) {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show position-fixed`;
    alertDiv.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;

    document.body.appendChild(alertDiv);

    // Auto dismiss
    if (duration > 0) {
        setTimeout(() => {
            alertDiv.remove();
        }, duration);
    }

    return alertDiv;
}

function showSuccessNotification(message, duration = 3000) {
    showNotification(message, 'success', duration);
}

function showErrorNotification(message, duration = 5000) {
    showNotification(message, 'danger', duration);
}

function showWarningNotification(message, duration = 4000) {
    showNotification(message, 'warning', duration);
}

function showInfoNotification(message, duration = 3000) {
    showNotification(message, 'info', duration);
}

// CSRF Token handling
function getCsrfToken() {
    return document.querySelector('meta[name="_csrf"]')?.content || '';
}

// API Utilities
function getApiBaseUrl() {
    return window.location.origin;
}

function apiCall(method, endpoint, data = null, options = {}) {
    const url = `${getApiBaseUrl()}${endpoint}`;
    const config = {
        method: method,
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-Token': getCsrfToken(),
            ...options.headers
        },
        ...options
    };

    if (data) {
        config.body = JSON.stringify(data);
    }

    return fetch(url, config)
        .then(response => {
            if (!response.ok) {
                throw new Error(`API Error: ${response.status} ${response.statusText}`);
            }
            return response.json();
        })
        .catch(error => {
            console.error('API Call Error:', error);
            throw error;
        });
}

// Local Storage Utilities
function storageSet(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
        console.error('Storage set error:', e);
    }
}

function storageGet(key, defaultValue = null) {
    try {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) : defaultValue;
    } catch (e) {
        console.error('Storage get error:', e);
        return defaultValue;
    }
}

function storageRemove(key) {
    try {
        localStorage.removeItem(key);
    } catch (e) {
        console.error('Storage remove error:', e);
    }
}

// Formatting Utilities
function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function formatDate(date) {
    return new Intl.DateTimeFormat('en-US', {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(new Date(date));
}

function formatTime(date) {
    return new Intl.DateTimeFormat('en-US', {
        hour: '2-digit',
        minute: '2-digit'
    }).format(new Date(date));
}

// Validation Utilities
function validateEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

function validateFileSize(file, maxSizeMB) {
    return file.size <= (maxSizeMB * 1024 * 1024);
}

function validateFileType(file, allowedTypes = []) {
    return allowedTypes.includes(file.type) ||
           allowedTypes.some(type => file.name.endsWith(type));
}

// Debounce Utility
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Throttle Utility
function throttle(func, limit) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// Copy to Clipboard
function copyToClipboard(text) {
    navigator.clipboard.writeText(text)
        .then(() => showSuccessNotification('Copied to clipboard!'))
        .catch(() => showErrorNotification('Failed to copy'));
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();

    // Setup dark mode toggle
    const darkModeCheckbox = document.getElementById('darkMode');
    if (darkModeCheckbox) {
        darkModeCheckbox.addEventListener('change', toggleTheme);
    }

    // Setup file input click on parent
    document.querySelectorAll('.upload-area').forEach(area => {
        area.addEventListener('click', (e) => {
            if (e.target.tagName !== 'BUTTON') {
                area.querySelector('input[type="file"]')?.click();
            }
        });
    });
});

// Keyboard Shortcuts
document.addEventListener('keydown', (e) => {
    // Shift+Enter in textarea to send (for chat)
    if (e.shiftKey && e.key === 'Enter') {
        const textarea = document.activeElement;
        if (textarea && textarea.tagName === 'TEXTAREA' && textarea.id === 'messageInput') {
            textarea.form.requestSubmit();
        }
    }

    // Ctrl/Cmd+K for search (future enhancement)
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        // TODO: Open search
    }
});

// Console Info
console.log('%c Enterprise AI Knowledge Assistant', 'font-size: 16px; font-weight: bold; color: #0d6efd;');
console.log('%c Version 1.0.0 | Powered by Spring AI + PostgreSQL', 'font-size: 12px; color: #6c757d;');

