// Chat-specific JavaScript

class ChatManager {
    constructor() {
        this.conversationId = document.querySelector('input[name="conversationId"]')?.value;
        this.messageList = document.getElementById('messageList');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.autoScroll = true;

        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupAutoScroll();
        this.setupTextareaResize();
    }

    setupEventListeners() {
        // Form submission
        if (this.chatForm) {
            this.chatForm.addEventListener('htmx:afterRequest', (e) => {
                this.onMessageSent();
            });
        }

        // Message input handlers
        if (this.messageInput) {
            // Handle Shift+Enter to send, Enter+Ctrl to new line
            this.messageInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey) {
                    e.preventDefault();
                    if (this.messageInput.value.trim()) {
                        this.chatForm.requestSubmit();
                    }
                }
            });

            // Prevent Enter key default behavior
            this.messageInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey) {
                    e.preventDefault();
                }
            });
        }

        // Citation click handlers
        this.messageList?.addEventListener('click', (e) => {
            const citation = e.target.closest('[data-citation]');
            if (citation) {
                this.showCitation(citation.dataset.citation);
            }
        });
    }

    setupAutoScroll() {
        if (!this.messageList) return;

        // Scroll to bottom on new messages
        const observer = new MutationObserver(() => {
            if (this.autoScroll) {
                this.messageList.scrollTop = this.messageList.scrollHeight;
            }
        });

        observer.observe(this.messageList, {
            childList: true,
            subtree: true,
            characterData: true
        });

        // Disable auto-scroll when user scrolls up
        this.messageList.addEventListener('scroll', () => {
            const isAtBottom =
                this.messageList.scrollHeight -
                (this.messageList.scrollTop + this.messageList.clientHeight) < 100;
            this.autoScroll = isAtBottom;
        });

        // Initial scroll
        this.messageList.scrollTop = this.messageList.scrollHeight;
    }

    setupTextareaResize() {
        if (!this.messageInput) return;

        const resizeTextarea = () => {
            this.messageInput.style.height = 'auto';
            this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 150) + 'px';
        };

        this.messageInput.addEventListener('input', resizeTextarea);
        this.messageInput.addEventListener('keydown', () => setTimeout(resizeTextarea, 0));
    }

    onMessageSent() {
        // Clear input
        if (this.messageInput) {
            this.messageInput.value = '';
            this.messageInput.style.height = 'auto';
        }

        // Show success notification
        showSuccessNotification('Message sent!', 2000);

        // Scroll to bottom
        setTimeout(() => {
            this.messageList.scrollTop = this.messageList.scrollHeight;
        }, 100);
    }

    showCitation(citationHash) {
        // Show citation details in modal
        const modal = new bootstrap.Modal(document.getElementById('citationModal'));
        modal.show();
    }

    loadConversation() {
        if (!this.conversationId) return;

        // Load conversation messages via HTMX
        htmx.ajax('GET', `/api/chat/messages?conversationId=${this.conversationId}`, {
            target: '#messageList',
            swap: 'innerHTML'
        });
    }

    clearChat() {
        if (confirm('Are you sure you want to clear this conversation?')) {
            this.messageList.innerHTML = `
                <div class="text-center text-muted py-5">
                    <i class="fas fa-comments fa-3x mb-3"></i>
                    <p>Start a conversation</p>
                </div>
            `;
        }
    }
}

// File Upload Manager
class FileUploadManager {
    constructor() {
        this.uploadArea = document.querySelector('.upload-area');
        this.fileInput = this.uploadArea?.querySelector('input[type="file"]');
        this.uploadForm = this.uploadArea?.closest('form');

        if (this.uploadArea) {
            this.init();
        }
    }

    init() {
        this.setupDragAndDrop();
        this.setupFileInput();
    }

    setupDragAndDrop() {
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, this.preventDefaults, false);
            document.body.addEventListener(eventName, this.preventDefaults, false);
        });

        ['dragenter', 'dragover'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, () => this.highlight(), false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, () => this.unhighlight(), false);
        });

        this.uploadArea.addEventListener('drop', (e) => this.handleDrop(e), false);
    }

    setupFileInput() {
        if (!this.fileInput) return;

        this.fileInput.addEventListener('change', (e) => {
            this.handleFiles(e.target.files);
        });

        // Click on upload area to trigger file input
        this.uploadArea.addEventListener('click', (e) => {
            if (e.target !== this.fileInput && !e.target.closest('button')) {
                this.fileInput.click();
            }
        });
    }

    preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    highlight() {
        this.uploadArea.classList.add('bg-light');
        this.uploadArea.style.borderColor = '#0d6efd';
    }

    unhighlight() {
        this.uploadArea.classList.remove('bg-light');
        this.uploadArea.style.borderColor = '#dee2e6';
    }

    handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        this.handleFiles(files);
    }

    handleFiles(files) {
        const allowedTypes = ['.pdf', '.txt', '.docx'];
        const maxSize = 50 * 1024 * 1024; // 50MB

        for (let file of files) {
            // Validate file type
            const hasValidExtension = allowedTypes.some(type => file.name.endsWith(type));
            if (!hasValidExtension) {
                showErrorNotification(`Invalid file type: ${file.name}. Allowed: PDF, TXT, DOCX`);
                continue;
            }

            // Validate file size
            if (file.size > maxSize) {
                showErrorNotification(`File too large: ${file.name}. Max size: 50MB`);
                continue;
            }

            // Upload file
            this.uploadFile(file);
        }
    }

    uploadFile(file) {
        const formData = new FormData();
        formData.append('file', file);

        const progressContainer = document.createElement('div');
        progressContainer.className = 'progress mb-3';
        progressContainer.innerHTML = `
            <div class="progress-bar" role="progressbar" style="width: 0%">
                <span>${file.name} (0%)</span>
            </div>
        `;
        this.uploadArea.parentElement.appendChild(progressContainer);

        // Use fetch for upload with progress
        fetch('/api/documents/upload', {
            method: 'POST',
            body: formData,
            headers: {
                'X-CSRF-Token': getCsrfToken()
            }
        })
        .then(response => response.json())
        .then(data => {
            progressContainer.remove();
            showSuccessNotification(`File uploaded: ${data.fileName}`);

            // Refresh document list
            htmx.ajax('GET', '/api/documents', {
                target: '#documentList',
                swap: 'innerHTML'
            });
        })
        .catch(error => {
            progressContainer.remove();
            showErrorNotification(`Upload failed: ${file.name}`);
            console.error('Upload error:', error);
        });
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    // Initialize chat manager if on chat page
    if (document.getElementById('chatForm')) {
        window.chatManager = new ChatManager();
    }

    // Initialize file upload manager if on documents page
    if (document.querySelector('.upload-area')) {
        window.fileUploadManager = new FileUploadManager();
    }
});

// Handle HTMX after settle (when all swaps are complete)
document.addEventListener('htmx:afterSettle', () => {
    // Re-initialize any Bootstrap components
    document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(el => {
        new bootstrap.Tooltip(el);
    });

    document.querySelectorAll('[data-bs-toggle="popover"]').forEach(el => {
        new bootstrap.Popover(el);
    });

    // Scroll to bottom if on chat page
    if (window.chatManager) {
        window.chatManager.messageList.scrollTop = window.chatManager.messageList.scrollHeight;
    }
});

// Handle HTMX errors
document.addEventListener('htmx:responseError', (event) => {
    console.error('HTMX Error:', event.detail);
    showErrorNotification('Failed to process request');
});

// Handle file input for attachments
document.addEventListener('change', (e) => {
    if (e.target.type === 'file' && e.target.name === 'attachment') {
        const file = e.target.files[0];
        if (file) {
            const fileName = file.name;
            const fileSize = formatBytes(file.size);
            showInfoNotification(`Attached: ${fileName} (${fileSize})`, 3000);
        }
    }
});

