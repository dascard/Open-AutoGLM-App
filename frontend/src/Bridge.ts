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
}

export default Bridge;
