// Bridge.ts

// Define the Android interface type
interface AndroidInterface {
    startTask(task: string): void;
    startTaskWithMode(task: string, mode: string): void;
    stopTask(): void;
    togglePause(): void;
    isPaused(): boolean;
    showToast(message: string): void;
    checkOverlayPermission(): boolean;
    requestOverlayPermission(): void;
    openAccessibilitySettings(): void;
    getApiConfigs(): string;
    saveApiConfig(configJson: string): void;
    deleteApiConfig(id: string): void;
    getCommandHistory(): string;
    clearCommandHistory(): void;
    // Shizuku methods
    checkShizukuAvailable(): boolean;
    checkShizukuPermission(): boolean;
    requestShizukuPermission(): void;
    bindShizukuService(): void;
    executeAdbCommand(command: string): string;
    getShizukuStatus(): string;
    // Settings methods
    getLogLevel(): number;
    setLogLevel(level: number): void;
    getDevMode(): boolean;
    setDevMode(enabled: boolean): void;
    getChatHistory(): string;
    saveChatHistory(json: string): void;
    getMaxSteps(): number;
    setMaxSteps(steps: number): void;
    // SoM Preview
    getSomPreview(): string;
    // Execution Mode
    getExecutionMode(): string;
    setExecutionMode(mode: string): void;
    // Visual Strategy
    getVisualStrategy(): string;
    setVisualStrategy(strategy: string): void;
    // File Log
    getFileLogContent(): string;
    // Task Lists
    getTaskLists(): string;
    saveTaskLists(json: string): void;
    // Coordinate Picker
    startCoordPicker(mode: string, x1: number, y1: number, x2: number, y2: number): void;
    stopCoordPicker(): void;
    // File Import/Export
    exportToFile(filename: string, content: string): void;
    importFromFile(): void;
}

// Extend Window interface
declare global {
    interface Window {
        Android?: AndroidInterface;

        // Callbacks exposed to Android
        updateLogs?: (logs: string) => void;
        updateStatus?: (status: string) => void;
        updateServiceStatus?: (isEnabled: string) => void;
        updatePauseState?: (isPaused: string) => void;
        showToast?: (message: string) => void;
        openSettings?: (data: string) => void;
        updateShizukuStatus?: (status: string) => void;
        handleBack?: () => boolean; // Returns true if handled, false to allow default behavior
        // Coordinate picker callbacks (set by frontend)
        onCoordPickerResult?: (result: { x1: number, y1: number, x2?: number, y2?: number }) => void;
        onCoordPickerCancelled?: () => void;
        // File import callback
        onFileImported?: (filename: string, content: string) => void;
    }
}

class Bridge {
    static startTask(task: string) {
        if (window.Android) {
            window.Android.startTask(task);
        } else {
            console.log('Mock: startTask', task);
        }
    }

    static startTaskWithMode(task: string, mode: 'accessibility' | 'shizuku') {
        if (window.Android) {
            if ('startTaskWithMode' in window.Android) {
                (window.Android as AndroidInterface).startTaskWithMode(task, mode);
            } else {
                // Fallback to old API
                window.Android.startTask(task);
            }
        } else {
            console.log('Mock: startTaskWithMode', task, mode);
        }
    }

    static stopTask() {
        if (window.Android) {
            window.Android.stopTask();
        } else {
            console.log('Mock: stopTask');
        }
    }

    static togglePause() {
        if (window.Android) {
            window.Android.togglePause();
        } else {
            console.log('Mock: togglePause');
        }
    }

    static checkOverlayPermission(): boolean {
        if (window.Android) {
            return window.Android.checkOverlayPermission();
        }
        return true; // Mock
    }

    static requestOverlayPermission() {
        if (window.Android) {
            window.Android.requestOverlayPermission();
        } else {
            console.log('Mock: requestOverlayPermission');
        }
    }

    static openAccessibilitySettings() {
        if (window.Android) {
            window.Android.openAccessibilitySettings();
        } else {
            console.log('Mock: openAccessibilitySettings');
        }
    }

    static getApiConfigs(): any[] {
        if (window.Android) {
            const json = window.Android.getApiConfigs();
            return JSON.parse(json);
        } else {
            console.log('Mock: getApiConfigs');
            return [];
        }
    }

    static saveApiConfig(config: any) {
        if (window.Android) {
            window.Android.saveApiConfig(JSON.stringify(config));
        } else {
            console.log('Mock: saveApiConfig', config);
        }
    }

    static deleteApiConfig(id: string) {
        if (window.Android) {
            window.Android.deleteApiConfig(id);
        } else {
            console.log('Mock: deleteApiConfig', id);
        }
    }

    static getCommandHistory(): string[] {
        if (window.Android) {
            const json = window.Android.getCommandHistory();
            return JSON.parse(json);
        } else {
            console.log('Mock: getCommandHistory');
            return ['打开微信', '打开浏览器', '搜索天气'];
        }
    }

    static clearCommandHistory() {
        if (window.Android) {
            window.Android.clearCommandHistory();
        } else {
            console.log('Mock: clearCommandHistory');
        }
    }

    // Register callbacks from Vue components
    static onLogUpdate(callback: (logs: any[]) => void) {
        window.updateLogs = (json: string) => {
            try {
                const logs = JSON.parse(json);
                callback(logs);
            } catch (e) {
                console.error('Failed to parse logs', e);
            }
        };
    }

    static onStatusUpdate(callback: (status: string) => void) {
        window.updateStatus = callback;
    }

    static onServiceStatusUpdate(callback: (isEnabled: boolean) => void) {
        window.updateServiceStatus = (isEnabled: string) => {
            callback(isEnabled === 'true');
        }
    }

    static onPauseStateUpdate(callback: (isPaused: boolean) => void) {
        window.updatePauseState = (isPaused: string) => {
            callback(isPaused === 'true');
        }
    }

    static onShowToast(callback: (msg: string) => void) {
        window.showToast = callback;
    }

    static onOpenSettings(callback: () => void) {
        window.openSettings = () => {
            callback();
        }
    }

    static onBackPressed(callback: () => boolean) {
        window.handleBack = callback;
    }

    // --- Shizuku Methods ---

    static checkShizukuAvailable(): boolean {
        if (window.Android) {
            return window.Android.checkShizukuAvailable();
        }
        return false; // Mock
    }

    static checkShizukuPermission(): boolean {
        if (window.Android) {
            return window.Android.checkShizukuPermission();
        }
        return false; // Mock
    }

    static requestShizukuPermission() {
        if (window.Android) {
            window.Android.requestShizukuPermission();
        } else {
            console.log('Mock: requestShizukuPermission');
        }
    }

    static bindShizukuService() {
        if (window.Android) {
            window.Android.bindShizukuService();
        } else {
            console.log('Mock: bindShizukuService');
        }
    }

    static executeAdbCommand(command: string): string {
        if (window.Android) {
            return window.Android.executeAdbCommand(command);
        }
        console.log('Mock: executeAdbCommand', command);
        return 'Mock result';
    }

    static getShizukuStatus(): any {
        if (window.Android) {
            const json = window.Android.getShizukuStatus();
            return JSON.parse(json);
        }
        // Mock status
        return {
            available: false,
            hasPermission: false,
            serviceBound: false,
            uid: -1,
            privilege: 'UNKNOWN'
        };
    }

    static onShizukuStatusUpdate(callback: (status: any) => void) {
        window.updateShizukuStatus = (json: string) => {
            try {
                callback(JSON.parse(json));
            } catch (e) {
                console.error('Failed to parse Shizuku status', e);
            }
        };
    }

    // --- Settings Methods ---

    static getLogLevel(): number {
        if (window.Android) {
            return window.Android.getLogLevel();
        }
        return 3; // Mock: INFO
    }

    static setLogLevel(level: number) {
        if (window.Android) {
            window.Android.setLogLevel(level);
        } else {
            console.log('Mock: setLogLevel', level);
        }
    }

    static getDevMode(): boolean {
        if (window.Android) {
            return window.Android.getDevMode();
        }
        return false; // Mock
    }

    static setDevMode(enabled: boolean) {
        if (window.Android) {
            window.Android.setDevMode(enabled);
        } else {
            console.log('Mock: setDevMode', enabled);
        }
    }

    static getChatHistory(): any[] {
        if (window.Android) {
            const json = window.Android.getChatHistory();
            return JSON.parse(json);
        }
        return []; // Mock
    }

    static saveChatHistory(sessions: any[]) {
        if (window.Android) {
            window.Android.saveChatHistory(JSON.stringify(sessions));
        } else {
            console.log('Mock: saveChatHistory', sessions);
        }
    }

    static getMaxSteps(): number {
        if (window.Android) {
            return window.Android.getMaxSteps();
        }
        return 50; // Mock default
    }

    static setMaxSteps(steps: number) {
        if (window.Android) {
            window.Android.setMaxSteps(steps);
        } else {
            console.log('Mock: setMaxSteps', steps);
        }
    }

    static getSomPreview(): string {
        if (window.Android) {
            return window.Android.getSomPreview();
        }
        console.log('Mock: getSomPreview');
        return ''; // Mock: empty string means no preview available
    }

    static getExecutionMode(): string {
        if (window.Android) {
            return window.Android.getExecutionMode();
        }
        return 'accessibility'; // Mock default
    }

    static setExecutionMode(mode: string) {
        if (window.Android) {
            window.Android.setExecutionMode(mode);
        } else {
            console.log('Mock: setExecutionMode', mode);
        }
    }

    static getVisualStrategy(): string {
        if (window.Android) {
            return window.Android.getVisualStrategy();
        }
        return 'auto'; // Mock default
    }

    static setVisualStrategy(strategy: string) {
        if (window.Android) {
            window.Android.setVisualStrategy(strategy);
        } else {
            console.log('Mock: setVisualStrategy', strategy);
        }
    }

    static getFileLogContent(): string {
        if (window.Android) {
            return window.Android.getFileLogContent();
        }
        return 'Mock: 日志文件功能需要在 Android 设备上运行';
    }

    // --- Task List Methods ---
    static getTaskLists(): any[] {
        if (window.Android) {
            const json = window.Android.getTaskLists();
            return JSON.parse(json);
        }
        return []; // Mock
    }

    static saveTaskLists(taskLists: any[]) {
        if (window.Android) {
            window.Android.saveTaskLists(JSON.stringify(taskLists));
        } else {
            console.log('Mock: saveTaskLists', taskLists);
        }
    }

    static startCoordPicker(
        mode: 'single' | 'swipe',
        x1: number,
        y1: number,
        x2: number = 500,
        y2: number = 800,
        onResult: (result: { x1: number, y1: number, x2?: number, y2?: number }) => void,
        onCancel: () => void
    ) {
        // Set up callbacks
        window.onCoordPickerResult = onResult;
        window.onCoordPickerCancelled = onCancel;

        if (window.Android && 'startCoordPicker' in window.Android) {
            window.Android.startCoordPicker(mode, x1, y1, x2, y2);
        } else {
            console.log('Mock: startCoordPicker', mode, x1, y1, x2, y2);
            // Mock: simulate confirmation after 1 second
            setTimeout(() => {
                onResult({ x1: 300, y1: 500, x2: mode === 'swipe' ? 300 : undefined, y2: mode === 'swipe' ? 900 : undefined });
            }, 1000);
        }
    }

    static stopCoordPicker() {
        if (window.Android && 'stopCoordPicker' in window.Android) {
            window.Android.stopCoordPicker();
        } else {
            console.log('Mock: stopCoordPicker');
        }
    }

    static exportToFile(filename: string, content: string) {
        if (window.Android && 'exportToFile' in window.Android) {
            window.Android.exportToFile(filename, content);
        } else {
            console.log('Mock: exportToFile', filename);
            // Browser fallback: download file
            const blob = new Blob([content], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            a.click();
            URL.revokeObjectURL(url);
        }
    }

    static importFromFile() {
        if (window.Android && 'importFromFile' in window.Android) {
            window.Android.importFromFile();
        } else {
            console.log('Mock: importFromFile');
            // Browser fallback: file input
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.txt,.script';
            input.onchange = (e) => {
                const file = (e.target as HTMLInputElement).files?.[0];
                if (file) {
                    const reader = new FileReader();
                    reader.onload = () => {
                        window.onFileImported?.(file.name, reader.result as string);
                    };
                    reader.readAsText(file);
                }
            };
            input.click();
        }
    }
}

export default Bridge;
