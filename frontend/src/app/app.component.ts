import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

interface ProgressStep {
  label: string;
  sublabel: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  activeFile: File | null = null;
  dragOver = false;
  isAnalyzing = false;
  errorMessage = '';
  
  // Settings
  requirementType: 'NEW' | 'UPDATE' = 'UPDATE';

  // Progress tracking
  progressStepIndex = 0;
  progressPercentage = 0;
  progressSteps: ProgressStep[] = [
    { label: 'File Upload', sublabel: 'Transmitting requirement document to Coordinator Agent...' },
    { label: 'MCP Server Initialization', sublabel: 'Connecting Coordinator Agent to Document MCP Server...' },
    { label: 'Document Analysis', sublabel: 'Extracting metadata, sections, and highlighted runs...' },
    { label: 'Requirement Summary', sublabel: 'Requirement Agent summarizing modifications & rules...' },
    { label: 'Task Engineering', sublabel: 'Task Agent mapping code impacts and test cases...' },
    { label: 'Report Compilation', sublabel: 'Coordinator Agent finalizing markdown implementation plan...' }
  ];

  // Results
  reportRaw = '';
  documentType = '';
  highlightsDetected = false;
  agentTrace: string[] = [];
  
  sections = {
    summary: '',
    rules: '',
    impact: '',
    tasks: '',
    testcases: ''
  };
  selectedTab: 'summary' | 'rules' | 'impact' | 'tasks' | 'testcases' | 'trace' = 'summary';
  fileName = '';
  downloadHref: string | null = null;

  constructor(private http: HttpClient, private sanitizer: DomSanitizer) {}

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.dragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver = false;
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      this.selectFile(event.dataTransfer.files[0]);
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectFile(input.files[0]);
    }
  }

  selectFile(file: File) {
    const ext = file.name.split('.').pop()?.toLowerCase();
    
    // Explicit MVP file type validation
    if (ext === 'xlsx' || ext === 'docx' || ext === 'txt' || ext === 'md') {
      this.errorMessage = '';
      this.activeFile = file;
    } else if (ext === 'xls' || ext === 'doc' || ext === 'pdf') {
      const formatName = ext.toUpperCase();
      this.errorMessage = `${formatName} files are not supported in this MVP. Supported formats are: .xlsx, .docx, .txt, .md. (${formatName} support is planned for future improvements).`;
      this.activeFile = null;
    } else {
      this.errorMessage = 'Invalid file type. Supported document formats are: .xlsx, .docx, .txt, .md.';
      this.activeFile = null;
    }
  }

  removeFile() {
    this.activeFile = null;
    this.errorMessage = '';
  }

  analyzeFile() {
    if (!this.activeFile) return;

    this.isAnalyzing = true;
    this.progressStepIndex = 0;
    this.progressPercentage = 10;
    this.errorMessage = '';
    this.reportRaw = '';
    this.agentTrace = [];

    // Create form data
    const formData = new FormData();
    formData.append('file', this.activeFile);
    formData.append('requirementType', this.requirementType);

    // Simulate progress steps during processing
    const progressInterval = setInterval(() => {
      if (this.progressStepIndex < this.progressSteps.length - 1) {
        this.progressStepIndex++;
        this.progressPercentage = Math.min((this.progressStepIndex + 1) * 15, 90);
      }
    }, 2500);

    // Make API Call
    this.http.post<any>('/api/analyze', formData).subscribe({
      next: (response) => {
        clearInterval(progressInterval);
        this.progressStepIndex = this.progressSteps.length - 1;
        this.progressPercentage = 100;

        setTimeout(() => {
          if (response.status === 'success') {
            this.reportRaw = response.report;
            this.fileName = response.fileName;
            this.documentType = response.documentType;
            this.highlightsDetected = response.highlightsDetected;
            this.agentTrace = response.trace || [];
            this.parseAndSplitReport(response.report);
            this.isAnalyzing = false;
          } else {
            this.errorMessage = response.message || 'An error occurred during analysis.';
            this.isAnalyzing = false;
          }
        }, 800);
      },
      error: (error) => {
        clearInterval(progressInterval);
        this.errorMessage = error.error?.message || 'Server connection failed. Ensure the Spring Boot backend is running.';
        this.isAnalyzing = false;
      }
    });
  }

  parseAndSplitReport(markdown: string) {
    this.sections = {
      summary: '',
      rules: '',
      impact: '',
      tasks: '',
      testcases: ''
    };

    // Split based on custom markdown headers
    const parts = markdown.split(/(?=## )/g);
    for (const part of parts) {
      const lines = part.trim().split('\n');
      if (lines.length === 0) continue;

      const header = lines[0].toLowerCase();
      const content = lines.slice(1).join('\n').trim();

      if (header.includes('summary')) {
        this.sections.summary = content;
      } else if (header.includes('rules')) {
        this.sections.rules = content;
      } else if (header.includes('impact')) {
        this.sections.impact = content;
      } else if (header.includes('tasks')) {
        this.sections.tasks = content;
      } else if (header.includes('cases') || header.includes('test')) {
        this.sections.testcases = content;
      }
    }

    // Fallbacks if splitting missed sections
    if (!this.sections.summary) this.sections.summary = markdown;

    // Create Download Blob
    const blob = new Blob([markdown], { type: 'text/markdown' });
    this.downloadHref = URL.createObjectURL(blob);
  }

  getSafeHtml(markdown: string): SafeHtml {
    const rawHtml = this.parseMarkdownToHtml(markdown);
    return this.sanitizer.bypassSecurityTrustHtml(rawHtml);
  }

  parseMarkdownToHtml(markdown: string): string {
    if (!markdown) return '';
    const lines = markdown.split('\n');
    let html = '';
    let inList = false;

    for (const line of lines) {
      const trimmed = line.trim();

      // Skip top level header in tabs to keep it clean
      if (trimmed.startsWith('# ') && !trimmed.toLowerCase().includes('report')) {
        html += `<h2 class="md-h1">${this.parseInline(trimmed.substring(2))}</h2>`;
        continue;
      }

      // Check lists
      if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        if (!inList) {
          html += '<ul class="md-list">';
          inList = true;
        }
        html += `<li>${this.parseInline(trimmed.substring(2))}</li>`;
        continue;
      } else {
        if (inList) {
          html += '</ul>';
          inList = false;
        }
      }

      if (trimmed.startsWith('### ')) {
        html += `<h3 class="md-h3">${this.parseInline(trimmed.substring(4))}</h3>`;
      } else if (trimmed.startsWith('## ')) {
        html += `<h2 class="md-h2">${this.parseInline(trimmed.substring(3))}</h2>`;
      } else if (trimmed.startsWith('> ')) {
        html += `<blockquote class="md-quote">${this.parseInline(trimmed.substring(2))}</blockquote>`;
      } else if (trimmed === '') {
        html += '<div class="md-spacer"></div>';
      } else {
        html += `<p class="md-p">${this.parseInline(line)}</p>`;
      }
    }

    if (inList) {
      html += '</ul>';
    }

    return html;
  }

  parseInline(text: string): string {
    let formatted = text;
    // Bold: **text**
    formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    // Italic: *text*
    formatted = formatted.replace(/\*(.*?)\*/g, '<em>$1</em>');
    // Code blocks: `code`
    formatted = formatted.replace(/`/g, ''); // Simple cleanup for inline code tags if needed
    formatted = text.replace(/`(.*?)`/g, '<code class="md-code">$1</code>');
    return formatted;
  }

  reset() {
    this.activeFile = null;
    this.reportRaw = '';
    this.downloadHref = null;
    this.errorMessage = '';
    this.documentType = '';
    this.highlightsDetected = false;
    this.agentTrace = [];
  }
}
