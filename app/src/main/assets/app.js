/* Smart Timetable Application Logic */

const GOOGLE_SCRIPT_URL =
    "https://script.google.com/macros/s/AKfycbyos-gMyLidhhNDFe9gESMcWdIQGrrbnJ3dJqiUM97oTmZzO8Vq9DLawF5nqMwLD3E/exec";

const LOCAL_STORAGE_KEY = "smartTimetableLockedVersion1";

const defaultData = {
    schoolName: "MY SMART SCHOOL",
    academicSession: "Session: 2026 - 2027",
    totalPeriods: 8,
    classes: [
        "Class 2", "Class 3", "Class 5", "Class 6",
        "Class 7", "Class 8", "Class 9", "Class 10"
    ],
    subjects: [
        "Math", "English", "Urdu", "Science",
        "Chemistry", "Physics", "Biology", "Computer",
        "Islamiat", "Pak Studies"
    ],
    teachers: [
        "Shahid Yasin", "M. Ahmad", "Zafar Iqbal", "M. Hassan Nawaz",
        "Hassan Tariq", "Nasir Ali", "Kaleem Abbas", "Mansoor Ahmad"
    ],
    incharges: {},
    teacherRoster: {},
    temporaryTimetables: {},
    subjectDays: {},
    classCoTeachers: {},
    classExtraSubjects: {}
};

let appData = {};
let masterLocked = false;
let assignmentMode = "teacher";
let activeItem = null;
let cloudSaveTimer = null;

function deepClone(value) {
    return JSON.parse(JSON.stringify(value));
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function escapeJs(value) {
    return String(value)
        .replaceAll("\\", "\\\\")
        .replaceAll("'", "\\'");
}

function getLocalDateKey() {
    const date = new Date();
    const pad = number => String(number).padStart(2, "0");
    return (
        date.getFullYear() +
        "-" +
        pad(date.getMonth() + 1) +
        "-" +
        pad(date.getDate())
    );
}

function formatDate(dateString) {
    if (!dateString) return "";
    const date = new Date(dateString + "T00:00:00");
    return date.toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "long",
        year: "numeric"
    });
}

const WEEK_DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function getNewSubjectDays() {
    return Array.from(document.querySelectorAll("#new-subject-days input:checked")).map(input => input.value);
}

function updateNewSubjectDayCount() {
    const count = getNewSubjectDays().length;
    const label = document.getElementById("new-subject-day-count");
    if (label) label.textContent = count + (count === 1 ? " day" : " days");
}

function renderNewSubjectDays() {
    const container = document.getElementById("new-subject-days");
    if (!container) return;
    container.innerHTML = WEEK_DAYS.map(day => `
        <label class="subject-day-check">
            <input type="checkbox" value="${day}" ${["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"].includes(day) ? "checked" : ""} onchange="updateNewSubjectDayCount()">
            ${day.slice(0, 3)}
        </label>
    `).join("");
    updateNewSubjectDayCount();
}

function toggleSubjectTeachingDay(subject, day) {
    if (blockIfMasterLocked()) return;
    const current = Array.isArray(appData.subjectDays[subject]) ? appData.subjectDays[subject] : [];
    appData.subjectDays[subject] = current.includes(day) ? current.filter(item => item !== day) : [...current, day];
    appData.subjectDays[subject] = WEEK_DAYS.filter(item => appData.subjectDays[subject].includes(item));
    saveMasterData();
    renderSubjectSchedules();
}

function renderSubjectSchedules() {
    const container = document.getElementById("subject-schedule-list");
    if (!container) return;
    if (!appData.subjects.length) {
        container.innerHTML = '<div style="padding:10px;text-align:center;color:#94a3b8;border:1px dashed #cbd5e1;border-radius:10px;font-size:.75rem">No subjects added</div>';
        return;
    }
    container.innerHTML = appData.subjects.map(subject => {
        const days = appData.subjectDays[subject] || [];
        return `<div class="subject-schedule-row">
            <div class="subject-schedule-top">
                <div class="subject-schedule-name">${escapeHtml(subject)}</div>
                <div style="display:flex;align-items:center;gap:6px">
                    <span class="subject-days-count">${days.length}${days.length === 1 ? " day" : " days"}</span>
                    <span class="subject-row-actions">
                        <button type="button" onclick="renameItem('subjects','${escapeJs(subject)}')">✏️</button>
                        <button type="button" onclick="removeItem('subjects','${escapeJs(subject)}')">×</button>
                    </span>
                </div>
            </div>
            <div class="subject-day-buttons">
                ${WEEK_DAYS.map(day => `<button type="button" class="subject-day-button ${days.includes(day) ? "active" : ""}" onclick="toggleSubjectTeachingDay('${escapeJs(subject)}','${day}')">${day.slice(0, 3)}</button>`).join("")}
            </div>
        </div>`;
    }).join("");
}

function checkDataIntegrity() {
    if (!Array.isArray(appData.classes)) appData.classes = [];
    if (!Array.isArray(appData.subjects)) appData.subjects = [];
    if (!Array.isArray(appData.teachers)) appData.teachers = [];
    if (!appData.incharges || typeof appData.incharges !== "object") appData.incharges = {};
    if (!appData.subjectDays || typeof appData.subjectDays !== "object") appData.subjectDays = {};

    appData.subjects.forEach(subject => {
        if (!Array.isArray(appData.subjectDays[subject])) {
            appData.subjectDays[subject] = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
        }
        appData.subjectDays[subject] = [...new Set(appData.subjectDays[subject])].filter(day => WEEK_DAYS.includes(day));
    });

    if (!appData.teacherRoster || typeof appData.teacherRoster !== "object") appData.teacherRoster = {};
    if (!appData.temporaryTimetables || typeof appData.temporaryTimetables !== "object") appData.temporaryTimetables = {};
    if (!appData.classCoTeachers || typeof appData.classCoTeachers !== "object") appData.classCoTeachers = {};
    if (!appData.classExtraSubjects || typeof appData.classExtraSubjects !== "object") appData.classExtraSubjects = {};

    appData.classes.forEach(className => {
        if (!Array.isArray(appData.classCoTeachers[className])) appData.classCoTeachers[className] = [];
        while (appData.classCoTeachers[className].length < appData.totalPeriods) {
            appData.classCoTeachers[className].push("None");
        }
        appData.classCoTeachers[className] = appData.classCoTeachers[className].slice(0, appData.totalPeriods)
            .map(t => appData.teachers.includes(t) ? t : "None");

        if (!Array.isArray(appData.classExtraSubjects[className])) {
            appData.classExtraSubjects[className] = Array(appData.totalPeriods).fill("None");
        }

        if (!appData.incharges[className]) {
            appData.incharges[className] = "None";
        }
    });

    appData.teachers.forEach(teacher => {
        if (!Array.isArray(appData.teacherRoster[teacher])) {
            appData.teacherRoster[teacher] = [];
        }
        while (appData.teacherRoster[teacher].length < appData.totalPeriods) {
            appData.teacherRoster[teacher].push({ class: "None", subject: "None" });
        }
        appData.teacherRoster[teacher] = appData.teacherRoster[teacher].slice(0, appData.totalPeriods);
    });
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeApplication);
} else {
    initializeApplication();
}

function initializeApplication() {
    const localData = localStorage.getItem(LOCAL_STORAGE_KEY);
    if (localData) {
        try {
            appData = JSON.parse(localData);
        } catch (error) {
            appData = deepClone(defaultData);
        }
    } else {
        appData = deepClone(defaultData);
    }

    checkDataIntegrity();

    const schoolEl = document.getElementById("school-name");
    if (schoolEl) schoolEl.value = appData.schoolName || "";
    const sessionEl = document.getElementById("academic-session");
    if (sessionEl) sessionEl.value = appData.academicSession || "";
    const periodsEl = document.getElementById("total-periods");
    if (periodsEl) periodsEl.value = appData.totalPeriods || 8;
    const tempDateEl = document.getElementById("temporary-date");
    if (tempDateEl) tempDateEl.value = getLocalDateKey();

    activeItem = appData.teachers[0] || null;
    saveLocalData();
    renderEverything();
    updateMobileNavigation(1);

    // Background asynchronous cloud sync (does not block instant millisecond startup)
    if (!GOOGLE_SCRIPT_URL.startsWith("PASTE_")) {
        fetch(GOOGLE_SCRIPT_URL + "?action=getData&_=" + Date.now())
            .then(res => res.json())
            .then(result => {
                const cloudData = result.data || result;
                if (cloudData && Array.isArray(cloudData.teachers)) {
                    appData = cloudData;
                    delete appData.masterLocked;
                    masterLocked = Boolean(result.masterLocked ?? cloudData.masterLocked ?? false);
                    checkDataIntegrity();
                    saveLocalData();
                    renderEverything();
                }
            })
            .catch(error => {
                console.warn("Background cloud sync note:", error);
            });
    }
}

function renderEverything() {
    renderNewSubjectDays();
    renderSubjectSchedules();
    renderChips();
    applyMasterLockUI();
    renderAssignment();
    renderTemporaryTimetable();
    renderMasterPrint();
}

const mobilePageNames = {
    1: "Setup",
    2: "Assign Duties",
    3: "Print Center",
    4: "Temporary Timetable"
};

function toggleMobileMenu() {
    const drawer = document.getElementById("mobile-drawer");
    const overlay = document.getElementById("menu-overlay");
    const toggle = document.getElementById("menu-toggle");
    const opening = !drawer.classList.contains("open");
    drawer.classList.toggle("open", opening);
    overlay.classList.toggle("open", opening);
    toggle.classList.toggle("open", opening);
    document.body.classList.toggle("menu-open", opening);
    toggle.setAttribute("aria-label", opening ? "Close menu" : "Open menu");
}

function closeMobileMenu() {
    document.getElementById("mobile-drawer")?.classList.remove("open");
    document.getElementById("menu-overlay")?.classList.remove("open");
    document.getElementById("menu-toggle")?.classList.remove("open");
    document.body.classList.remove("menu-open");
}

function navigateFromMenu(pageNumber) {
    closeMobileMenu();
    switchPage(pageNumber);
}

function updateMobileNavigation(pageNumber) {
    document.querySelectorAll(".drawer-nav-btn").forEach(button => button.classList.remove("active"));
    document.getElementById("drawer-nav" + pageNumber)?.classList.add("active");
    const currentPage = document.getElementById("mobile-current-page");
    if (currentPage) currentPage.textContent = mobilePageNames[pageNumber] || "Smart Timetable";
    const brand = document.getElementById("mobile-brand-title");
    const drawerSchool = document.getElementById("drawer-school");
    const drawerSession = document.getElementById("drawer-session");
    if (brand) brand.textContent = appData.schoolName || "Smart Timetable";
    if (drawerSchool) drawerSchool.textContent = appData.schoolName || "Smart Timetable";
    if (drawerSession) drawerSession.textContent = appData.academicSession || "Academic Session";
}

async function switchPage(pageNumber) {
    document.querySelectorAll(".page").forEach(page => page.classList.remove("active"));
    document.querySelectorAll(".navbar button").forEach(button => button.classList.remove("active"));
    document.getElementById("page" + pageNumber)?.classList.add("active");
    document.getElementById("nav" + pageNumber)?.classList.add("active");
    updateMobileNavigation(pageNumber);

    await refreshMasterLockStatus();

    if (pageNumber === 2) renderAssignment();
    if (pageNumber === 3) renderMasterPrint();
    if (pageNumber === 4) renderTemporaryTimetable();

    window.scrollTo(0, 0);
}

function blockIfMasterLocked() {
    if (!masterLocked) return false;
    alert("🔒 Timetable is locked.\n\nOnly temporary leave and substitute duties may be changed.");
    return true;
}

function applyMasterLockUI() {
    const lockBar = document.getElementById("master-lock-bar");
    const title = document.getElementById("master-lock-title");
    const message = document.getElementById("master-lock-message");
    const button = document.getElementById("master-lock-button");

    lockBar.classList.toggle("locked", masterLocked);

    if (masterLocked) {
        title.textContent = "🔒 Timetable Locked";
        message.textContent = "Only temporary leave and substitute duties can be changed.";
        button.textContent = "🔓 Unlock with PIN";
    } else {
        title.textContent = "🔓 Timetable Unlocked";
        message.textContent = "Timetable can be edited.";
        button.textContent = "🔒 Lock Timetable";
    }

    document.getElementById("master-setup-area").classList.toggle("master-locked-ui", masterLocked);
    document.getElementById("master-assignment-area").classList.toggle("master-locked-ui", masterLocked);

    const drawerStatus = document.getElementById("drawer-lock-status");
    const drawerTitle = document.getElementById("drawer-lock-title");
    if (drawerStatus) drawerStatus.classList.toggle("locked", masterLocked);
    if (drawerTitle) drawerTitle.textContent = masterLocked ? "Timetable Locked" : "Timetable Unlocked";
}

async function toggleMasterLock() {
    if (GOOGLE_SCRIPT_URL.startsWith("PASTE_")) {
        alert("Please configure Google Apps Script /exec URL first.");
        return;
    }

    const requiredStatus = !masterLocked;
    const pin = prompt(
        requiredStatus
            ? "Enter administrator PIN to lock timetable:"
            : "Enter administrator PIN to unlock timetable:"
    );

    if (pin === null || pin.trim() === "") return;

    const button = document.getElementById("master-lock-button");
    button.disabled = true;
    button.textContent = "Please wait...";

    try {
        const result = await postToGoogleSheets({
            action: "setMasterLock",
            locked: requiredStatus,
            pin: pin.trim()
        });

        if (!result.success) {
            alert(result.message || "Lock request failed.");
            return;
        }

        masterLocked = Boolean(result.masterLocked);
        applyMasterLockUI();
        alert(result.message || (masterLocked ? "Timetable locked." : "Timetable unlocked."));
    } catch (error) {
        console.error(error);
        alert("Could not connect to Google Apps Script.");
    } finally {
        button.disabled = false;
        applyMasterLockUI();
    }
}

async function refreshMasterLockStatus() {
    if (GOOGLE_SCRIPT_URL.startsWith("PASTE_")) return;
    try {
        const response = await fetch(GOOGLE_SCRIPT_URL + "?action=getLockStatus&_=" + Date.now());
        const result = await response.json();
        if (result.success) {
            masterLocked = Boolean(result.masterLocked);
            applyMasterLockUI();
        }
    } catch (error) {
        console.warn("Lock status refresh failed.", error);
    }
}

function updateSettings() {
    if (blockIfMasterLocked()) return;
    appData.schoolName = document.getElementById("school-name").value || "MY SMART SCHOOL";
    appData.academicSession = document.getElementById("academic-session").value || "Session: 2026 - 2027";
    const periodValue = Number(document.getElementById("total-periods").value);
    if (periodValue >= 1 && periodValue <= 15) {
        appData.totalPeriods = periodValue;
    }
    checkDataIntegrity();
    saveMasterData();
}

function addItem(type, inputId) {
    if (blockIfMasterLocked()) return;
    const input = document.getElementById(inputId);
    const value = input.value.trim();
    if (!value) return;

    if (appData[type].includes(value)) {
        alert('"' + value + '" already exists.');
        return;
    }

    appData[type].push(value);
    if (type === "teachers") appData.teacherRoster[value] = [];
    if (type === "classes") appData.incharges[value] = "None";

    checkDataIntegrity();
    input.value = "";
    saveMasterData();
    renderEverything();
}

function renameItem(type, oldValue) {
    if (blockIfMasterLocked()) return;
    let newValue = prompt("Enter new name:", oldValue);
    if (!newValue) return;
    newValue = newValue.trim();
    if (!newValue || newValue === oldValue) return;

    if (appData[type].includes(newValue)) {
        alert('"' + newValue + '" already exists.');
        return;
    }

    const index = appData[type].indexOf(oldValue);
    appData[type][index] = newValue;

    if (type === "teachers") {
        appData.teacherRoster[newValue] = appData.teacherRoster[oldValue];
        delete appData.teacherRoster[oldValue];
        Object.keys(appData.incharges).forEach(className => {
            if (appData.incharges[className] === oldValue) appData.incharges[className] = newValue;
        });
    }

    if (type === "classes") {
        appData.incharges[newValue] = appData.incharges[oldValue] || "None";
        delete appData.incharges[oldValue];
        appData.teachers.forEach(teacher => {
            appData.teacherRoster[teacher].forEach(duty => {
                if (duty.class === oldValue) duty.class = newValue;
            });
        });
    }

    if (type === "subjects") {
        appData.subjectDays[newValue] = appData.subjectDays[oldValue] || ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
        delete appData.subjectDays[oldValue];
        appData.teachers.forEach(teacher => {
            appData.teacherRoster[teacher].forEach(duty => {
                if (duty.subject === oldValue) duty.subject = newValue;
            });
        });
    }

    activeItem = newValue;
    saveMasterData();
    renderEverything();
}

function removeItem(type, value) {
    if (blockIfMasterLocked()) return;
    if (!confirm('Delete "' + value + '"?')) return;

    appData[type] = appData[type].filter(item => item !== value);

    if (type === "teachers") {
        delete appData.teacherRoster[value];
        Object.keys(appData.incharges).forEach(className => {
            if (appData.incharges[className] === value) appData.incharges[className] = "None";
        });
    }

    if (type === "subjects") {
        delete appData.subjectDays[value];
    }

    if (type === "classes") {
        delete appData.incharges[value];
        appData.teachers.forEach(teacher => {
            appData.teacherRoster[teacher].forEach(duty => {
                if (duty.class === value) {
                    duty.class = "None";
                    duty.subject = "None";
                }
            });
        });
    }

    activeItem = null;
    saveMasterData();
    renderEverything();
}

function renderChips() {
    const types = ["classes", "subjects", "teachers"];
    types.forEach(type => {
        const container = document.getElementById(type + "-chips");
        if (!container) return;
        container.innerHTML = appData[type].map(value => {
            const jsValue = escapeJs(value);
            return `
                <span class="chip">
                    ${escapeHtml(value)}
                    <button type="button" onclick="renameItem('${type}', '${jsValue}')">✏️</button>
                    <button type="button" onclick="removeItem('${type}', '${jsValue}')">×</button>
                </span>
            `;
        }).join("");
    });

    const counts = {
        classes: appData.classes.length,
        subjects: appData.subjects.length,
        teachers: appData.teachers.length
    };
    Object.entries(counts).forEach(([type, count]) => {
        const badge = document.getElementById(type + "-count");
        if (badge) badge.textContent = count;
    });
    const classTotal = document.getElementById("setup-class-total");
    const subjectTotal = document.getElementById("setup-subject-total");
    const teacherTotal = document.getElementById("setup-teacher-total");
    if (classTotal) classTotal.textContent = counts.classes;
    if (subjectTotal) subjectTotal.textContent = counts.subjects;
    if (teacherTotal) teacherTotal.textContent = counts.teachers;
}

function setAssignmentMode(mode) {
    assignmentMode = mode;
    document.querySelectorAll(".assign-mode-btn").forEach(button => button.classList.remove("active"));
    document.getElementById("assign-mode-" + mode)?.classList.add("active");
    if (mode === "teacher") activeItem = appData.teachers[0] || null;
    if (mode === "class") activeItem = appData.classes[0] || null;
    renderAssignment();
}

function selectAssignmentItem(value) {
    activeItem = value;
    renderAssignment();
}

function getTeacherLoad(teacher) {
    return (appData.teacherRoster[teacher] || []).filter(duty => duty.class !== "None").length;
}

function getClassLoad(className) {
    let count = 0;
    for (let period = 0; period < appData.totalPeriods; period++) {
        if (appData.teachers.some(teacher => appData.teacherRoster[teacher]?.[period]?.class === className)) count++;
    }
    return count;
}

function updateAssignmentSummary(assignedCount, totalCount) {
    const progress = document.getElementById("assignment-progress");
    const percent = totalCount ? Math.round((assignedCount / totalCount) * 100) : 0;
    if (progress) {
        progress.innerHTML = `<div class="assign-progress-text"><span>Assigned ${assignedCount}/${totalCount}</span><span>${percent}%</span></div><div class="assign-progress-track"><div class="assign-progress-fill" style="width:${percent}%"></div></div>`;
    }
    document.getElementById("assign-teacher-total").textContent = appData.teachers.length;
    document.getElementById("assign-class-total").textContent = appData.classes.length;
    document.getElementById("assign-period-total").textContent = appData.totalPeriods;
}

function renderAssignment() {
    const sidebar = document.getElementById("assignment-sidebar");
    const cards = document.getElementById("assignment-cards");
    const title = document.getElementById("assignment-title");
    const subtitle = document.getElementById("assignment-subtitle");

    document.querySelectorAll(".assign-mode-btn").forEach(button => button.classList.remove("active"));
    document.getElementById("assign-mode-" + assignmentMode)?.classList.add("active");

    if (assignmentMode === "incharge") {
        sidebar.style.display = "none";
        title.textContent = "Class Incharges";
        subtitle.textContent = "Select the main teacher for every class.";
        cards.className = "cards incharge-grid";
        cards.innerHTML = appData.classes.length ? appData.classes.map(className => `
            <div class="card incharge-card">
                <div class="incharge-class">${escapeHtml(className)}</div>
                <div class="incharge-note">Main class teacher</div>
                <select onchange="updateIncharge('${escapeJs(className)}', this.value)">
                    <option value="None">Select Incharge</option>
                    ${appData.teachers.map(teacher => `<option value="${escapeHtml(teacher)}" ${appData.incharges[className] === teacher ? "selected" : ""}>${escapeHtml(teacher)}</option>`).join("")}
                </select>
            </div>`).join("") : `<div class="empty-assignment">Add classes first.</div>`;
        updateAssignmentSummary(appData.classes.filter(c => appData.incharges[c] && appData.incharges[c] !== "None").length, appData.classes.length);
        applyMasterLockUI();
        initializeSmartSelects(cards);
        return;
    }

    sidebar.style.display = "block";
    cards.className = "cards";
    const list = assignmentMode === "teacher" ? appData.teachers : appData.classes;
    if (!activeItem || !list.includes(activeItem)) activeItem = list[0] || null;
    const sidebarLabel = assignmentMode === "teacher" ? "Instructor List" : "Class List";
    sidebar.innerHTML = `<div class="assign-sidebar-head"><div class="assign-sidebar-title">${sidebarLabel}</div><div class="assign-sidebar-note">Tap a record to edit periods</div></div>` + list.map(item => {
        const load = assignmentMode === "teacher" ? getTeacherLoad(item) : getClassLoad(item);
        return `<button class="sidebar-button ${item === activeItem ? "active" : ""}" onclick="selectAssignmentItem('${escapeJs(item)}')"><span class="sidebar-name">${escapeHtml(item)}</span><span class="sidebar-load">${load}/${appData.totalPeriods}</span></button>`;
    }).join("");

    title.textContent = activeItem || "No item selected";
    subtitle.textContent = assignmentMode === "teacher" ? "Assign class and subject for each period." : "Assign teacher and subject. Busy teachers are marked in red.";
    cards.innerHTML = "";
    if (!activeItem) {
        cards.innerHTML = `<div class="empty-assignment">No records available. Add records from Setup first.</div>`;
        updateAssignmentSummary(0, appData.totalPeriods);
        return;
    }
    if (assignmentMode === "teacher") renderTeacherAssignment();
    else renderClassAssignment();
    applyMasterLockUI();
}

function renderTeacherAssignment() {
    const cards = document.getElementById("assignment-cards");
    let html = "";
    let assigned = 0;
    for (let period = 0; period < appData.totalPeriods; period++) {
        const duty = appData.teacherRoster[activeItem]?.[period] || { class: "None", subject: "None" };
        const isAssigned = duty.class !== "None";
        if (isAssigned) assigned++;
        html += `<div class="card ${isAssigned ? "period-card-assigned" : "period-card-free"}">
            <div class="period-head"><span class="period-badge">Period ${period + 1}</span><span class="period-state ${isAssigned ? "assigned" : ""}">${isAssigned ? "Assigned" : "Free"}</span></div>
            <label class="label">Class</label>
            <select onchange="updateTeacherDuty(${period}, 'class', this.value)">
                <option value="None">Free / No Class</option>
                ${appData.classes.map(c => `<option value="${escapeHtml(c)}" ${duty.class === c ? "selected" : ""}>${escapeHtml(c)}</option>`).join("")}
            </select>
            <label class="label">Subject</label>
            <select ${!isAssigned ? "disabled" : ""} onchange="updateTeacherDuty(${period}, 'subject', this.value)">
                <option value="None">Select Subject</option>
                ${appData.subjects.map(subject => `<option value="${escapeHtml(subject)}" ${duty.subject === subject ? "selected" : ""}>${escapeHtml(subject)}</option>`).join("")}
            </select>
        </div>`;
    }
    cards.innerHTML = html;
    updateAssignmentSummary(assigned, appData.totalPeriods);
    initializeSmartSelects(cards);
}

function renderClassAssignment() {
    const cards = document.getElementById("assignment-cards");
    let html = "";
    let assignedCount = 0;
    for (let period = 0; period < appData.totalPeriods; period++) {
        const assignedTeachers = appData.teachers.filter(teacher =>
            appData.teacherRoster[teacher]?.[period]?.class === activeItem
        );
        const selectedTeacher = assignedTeachers[0] || "None";
        const selectedCoTeacher = appData.classCoTeachers?.[activeItem]?.[period] || "None";
        const subjectSource = selectedTeacher !== "None" ? selectedTeacher : selectedCoTeacher;
        const selectedSubject = subjectSource !== "None"
            ? (appData.teacherRoster[subjectSource]?.[period]?.subject || "None") : "None";

        if (!Array.isArray(appData.classExtraSubjects[activeItem])) {
            appData.classExtraSubjects[activeItem] = Array(appData.totalPeriods).fill("None");
        }
        const selectedExtraSubject = appData.classExtraSubjects[activeItem][period] || "None";
        const isAssigned = selectedTeacher !== "None" || selectedCoTeacher !== "None";
        if (isAssigned) assignedCount++;

        const makeTeacherOptions = (selected, emptyLabel) => {
            let options = `<option value="None">${emptyLabel}</option>`;
            appData.teachers.forEach(teacher => {
                const duty = appData.teacherRoster[teacher]?.[period] || { class: "None" };
                const busy = duty.class !== "None" && duty.class !== activeItem;
                const text = busy ? `${teacher} | Busy: ${duty.class}` : `${teacher} | ${duty.class === activeItem ? "Assigned Here" : "Free"}`;
                options += `<option value="${escapeHtml(teacher)}" ${selected === teacher ? "selected" : ""}>${escapeHtml(text)}</option>`;
            });
            return options;
        };

        const subjectOptions = `<option value="None">Select Subject</option>` +
            appData.subjects.map(subject => `<option value="${escapeHtml(subject)}" ${selectedSubject === subject ? "selected" : ""}>${escapeHtml(subject)}</option>`).join("");
        const extraSubjectOptions = `<option value="None">No Extra Subject</option>` +
            appData.subjects.map(subject => `<option value="${escapeHtml(subject)}" ${selectedExtraSubject === subject ? "selected" : ""}>${escapeHtml(subject)}</option>`).join("");

        html += `<div class="card ${isAssigned ? "period-card-assigned" : "period-card-free"}">
            <div class="period-head"><span class="period-badge">Period ${period + 1}</span><span class="period-state ${isAssigned ? "assigned" : ""}">${isAssigned ? "Assigned" : "Free"}</span></div>
            <label class="label">Assigned Teacher</label>
            <select onchange="updateClassTeacher(${period}, '${escapeJs(selectedTeacher)}', this.value)">${makeTeacherOptions(selectedTeacher, "Select Assigned Teacher")}</select>
            <label class="label">Co-Teacher</label>
            <select onchange="updateClassCoTeacher(${period}, this.value)">${makeTeacherOptions(selectedCoTeacher, "No Co-Teacher")}</select>
            <label class="label">Subject</label>
            <select ${!isAssigned ? "disabled" : ""} onchange="updateClassSubject(${period}, this.value)">${subjectOptions}</select>
            <label class="label">Extra Subject (Optional)</label>
            <select ${!isAssigned ? "disabled" : ""} onchange="updateClassExtraSubject(${period}, this.value)">${extraSubjectOptions}</select>
        </div>`;
    }
    cards.innerHTML = html;
    updateAssignmentSummary(assignedCount, appData.totalPeriods);
    initializeSmartSelects(cards);
}

function updateIncharge(className, teacher) {
    if (blockIfMasterLocked()) return;
    appData.incharges[className] = teacher;
    saveMasterData();
}

function updateTeacherDuty(period, field, value) {
    if (blockIfMasterLocked()) return;
    appData.teacherRoster[activeItem][period][field] = value;
    if (field === "class" && value === "None") {
        appData.teacherRoster[activeItem][period].subject = "None";
    }
    saveMasterData();
    renderAssignment();
}

function updateClassTeacher(period, oldTeacher, newTeacher) {
    if (blockIfMasterLocked()) return;
    const coTeacher = appData.classCoTeachers?.[activeItem]?.[period] || "None";
    const oldSubject = oldTeacher !== "None" ? appData.teacherRoster[oldTeacher]?.[period]?.subject : "None";
    if (newTeacher !== "None") {
        const duty = appData.teacherRoster[newTeacher]?.[period] || { class: "None", subject: "None" };
        if (duty.class !== "None" && duty.class !== activeItem && !confirm(`⚠️ ${newTeacher} is busy in ${duty.class}, Period ${period + 1}.\n\nOverride that duty?`)) {
            renderAssignment();
            return;
        }
    }
    if (oldTeacher !== "None" && oldTeacher !== newTeacher && oldTeacher !== coTeacher) {
        appData.teacherRoster[oldTeacher][period] = { class: "None", subject: "None" };
    }
    if (newTeacher !== "None") {
        appData.teacherRoster[newTeacher][period] = { class: activeItem, subject: oldSubject || "None" };
    }
    saveMasterData();
    renderAssignment();
}

function updateClassCoTeacher(period, newTeacher) {
    if (blockIfMasterLocked()) return;
    if (!appData.classCoTeachers[activeItem]) appData.classCoTeachers[activeItem] = Array(appData.totalPeriods).fill("None");
    const oldTeacher = appData.classCoTeachers[activeItem][period] || "None";
    const primary = appData.teachers.find(t => appData.teacherRoster[t]?.[period]?.class === activeItem) || "None";
    const subject = primary !== "None" ? appData.teacherRoster[primary][period].subject : "None";

    if (newTeacher !== "None") {
        const duty = appData.teacherRoster[newTeacher]?.[period] || { class: "None" };
        if (duty.class !== "None" && duty.class !== activeItem && !confirm(`⚠️ ${newTeacher} is busy in ${duty.class}, Period ${period + 1}.\n\nOverride that duty?`)) {
            renderAssignment();
            return;
        }
    }
    appData.classCoTeachers[activeItem][period] = newTeacher;
    if (oldTeacher !== "None" && oldTeacher !== primary && oldTeacher !== newTeacher) {
        appData.teacherRoster[oldTeacher][period] = { class: "None", subject: "None" };
    }
    if (newTeacher !== "None" && newTeacher !== primary) {
        appData.teacherRoster[newTeacher][period] = { class: activeItem, subject: subject || "None" };
    }
    saveMasterData();
    renderAssignment();
}

function updateClassSubject(period, subject) {
    if (blockIfMasterLocked()) return;
    const primary = appData.teachers.find(t => appData.teacherRoster[t]?.[period]?.class === activeItem) || "None";
    const coTeacher = appData.classCoTeachers?.[activeItem]?.[period] || "None";
    [...new Set([primary, coTeacher])].forEach(teacher => {
        if (teacher !== "None" && appData.teacherRoster[teacher]?.[period]) {
            appData.teacherRoster[teacher][period].subject = subject || "None";
        }
    });
    saveMasterData();
    renderAssignment();
}

function updateClassExtraSubject(period, subject) {
    if (blockIfMasterLocked()) return;
    if (!Array.isArray(appData.classExtraSubjects[activeItem])) {
        appData.classExtraSubjects[activeItem] = Array(appData.totalPeriods).fill("None");
    }
    appData.classExtraSubjects[activeItem][period] = subject || "None";
    saveMasterData();
    renderAssignment();
}

function getTemporaryRecord() {
    const dateInput = document.getElementById("temporary-date");
    const dateKey = (dateInput && dateInput.value) || getLocalDateKey();

    if (!appData.temporaryTimetables || typeof appData.temporaryTimetables !== "object") {
        appData.temporaryTimetables = {};
    }
    if (!appData.temporaryTimetables[dateKey] || typeof appData.temporaryTimetables[dateKey] !== "object") {
        appData.temporaryTimetables[dateKey] = { leaveTeachers: [], substitutions: {} };
    }

    const record = appData.temporaryTimetables[dateKey];
    if (!Array.isArray(record.leaveTeachers)) record.leaveTeachers = [];
    if (!record.substitutions || typeof record.substitutions !== "object") record.substitutions = {};

    record.leaveTeachers = [...new Set(record.leaveTeachers)]
        .filter(teacher => typeof teacher === "string")
        .map(teacher => teacher.trim())
        .filter(teacher => teacher && appData.teachers.includes(teacher));

    Object.keys(record.substitutions).forEach(key => {
        const substitute = record.substitutions[key];
        const absentTeacher = key.split("__")[0];
        if (!record.leaveTeachers.includes(absentTeacher) || substitute === "None" || !appData.teachers.includes(substitute)) {
            delete record.substitutions[key];
        }
    });

    return record;
}

function getAffectedDuties() {
    const record = getTemporaryRecord();
    const duties = [];

    record.leaveTeachers.forEach(absentTeacher => {
        const teacherDuties = appData.teacherRoster[absentTeacher] || [];
        teacherDuties.forEach((duty, period) => {
            if (duty.class !== "None") {
                duties.push({
                    absentTeacher: absentTeacher,
                    period: period,
                    className: duty.class,
                    subject: duty.subject
                });
            }
        });
    });

    duties.sort((first, second) => first.period - second.period);
    return duties;
}

function toggleTeacherLeave(teacher, checked) {
    const record = getTemporaryRecord();
    if (checked) {
        if (!record.leaveTeachers.includes(teacher)) record.leaveTeachers.push(teacher);
    } else {
        record.leaveTeachers = record.leaveTeachers.filter(item => item !== teacher);
        Object.keys(record.substitutions).forEach(key => {
            if (key.startsWith(teacher + "__")) delete record.substitutions[key];
        });
    }
    saveTemporaryData();
    renderTemporaryTimetable();
}

function isTeacherAvailable(teacher, period, currentKey) {
    const record = getTemporaryRecord();
    if (record.leaveTeachers.includes(teacher)) return false;

    const permanentDuty = appData.teacherRoster[teacher]?.[period];
    if (permanentDuty && permanentDuty.class !== "None") return false;

    const alreadyAssigned = Object.entries(record.substitutions).some(([assignmentKey, assignedTeacher]) => {
        const keyPeriod = Number(assignmentKey.split("__").pop());
        return assignmentKey !== currentKey && assignedTeacher === teacher && keyPeriod === period;
    });

    return !alreadyAssigned;
}

function assignSubstitute(absentTeacher, period, substituteTeacher) {
    const record = getTemporaryRecord();
    const key = absentTeacher + "__" + period;

    if (substituteTeacher === "None") {
        delete record.substitutions[key];
    } else {
        if (!isTeacherAvailable(substituteTeacher, period, key)) {
            alert(substituteTeacher + " is not available in Period " + (period + 1) + ".");
            renderTemporaryTimetable();
            return;
        }
        record.substitutions[key] = substituteTeacher;
    }

    saveTemporaryData();
    renderTemporaryTimetable();
}

function autoAssignSubstitutes() {
    const record = getTemporaryRecord();
    const duties = getAffectedDuties();
    if (!duties.length) {
        alert("No affected duties found.");
        return;
    }

    const assignmentCount = {};
    appData.teachers.forEach(teacher => { assignmentCount[teacher] = 0; });
    Object.values(record.substitutions).forEach(teacher => {
        if (teacher && teacher !== "None") assignmentCount[teacher] = (assignmentCount[teacher] || 0) + 1;
    });

    duties.forEach(duty => {
        const key = duty.absentTeacher + "__" + duty.period;
        if (record.substitutions[key]) return;

        const availableTeachers = appData.teachers.filter(teacher =>
            isTeacherAvailable(teacher, duty.period, key)
        );

        availableTeachers.sort((first, second) => {
            const difference = (assignmentCount[first] || 0) - (assignmentCount[second] || 0);
            if (difference !== 0) return difference;
            return first.localeCompare(second);
        });

        if (availableTeachers.length) {
            const selectedTeacher = availableTeachers[0];
            record.substitutions[key] = selectedTeacher;
            assignmentCount[selectedTeacher]++;
        }
    });

    saveTemporaryData();
    renderTemporaryTimetable();
}

function clearTemporaryDate() {
    const dateKey = document.getElementById("temporary-date").value;
    if (!confirm("Clear temporary timetable for " + formatDate(dateKey) + "?")) return;
    delete appData.temporaryTimetables[dateKey];
    saveTemporaryData();
    renderTemporaryTimetable();
}

function filterTemporaryTeachers() {
    const query = (document.getElementById("temp-teacher-search")?.value || "").trim().toLowerCase();
    document.querySelectorAll("#leave-teacher-list label").forEach(label => {
        label.style.display = label.dataset.teacherName.includes(query) ? "flex" : "none";
    });
}

function clearLeaveTeachers() {
    const record = getTemporaryRecord();
    if (!record.leaveTeachers.length) {
        renderTemporaryTimetable();
        return;
    }
    if (!confirm("Remove all teachers from leave for the selected date?")) return;
    record.leaveTeachers = [];
    record.substitutions = {};
    saveTemporaryData();
    renderTemporaryTimetable();
}

function clearSubstituteAssignments() {
    const record = getTemporaryRecord();
    if (!Object.keys(record.substitutions || {}).length) return;
    if (!confirm("Clear all substitute assignments for this date? Leave selections will remain.")) return;
    record.substitutions = {};
    saveTemporaryData();
    renderTemporaryTimetable();
}

function renderTemporaryTimetable() {
    const record = getTemporaryRecord();
    const duties = getAffectedDuties();
    const leaveList = document.getElementById("leave-teacher-list");
    const selectedCount = record.leaveTeachers.length;
    const assignedCount = duties.filter(duty => {
        const substitute = record.substitutions[duty.absentTeacher + "__" + duty.period];
        return substitute && substitute !== "None" && appData.teachers.includes(substitute);
    }).length;
    const pendingCount = Math.max(0, duties.length - assignedCount);

    leaveList.innerHTML = appData.teachers.map(teacher => {
        const onLeave = record.leaveTeachers.includes(teacher);
        const permanentLoad = (appData.teacherRoster[teacher] || []).filter(duty => duty.class !== "None").length;
        return `<label class="${onLeave ? "temp-on-leave" : ""}" data-teacher-name="${escapeHtml(teacher.toLowerCase())}">
            <span class="leave-teacher-name"><input type="checkbox" ${onLeave ? "checked" : ""} onchange="toggleTeacherLeave('${escapeJs(teacher)}', this.checked)"><span>${escapeHtml(teacher)}</span></span>
            <span class="leave-duty-count">${permanentLoad}/${appData.totalPeriods}</span>
        </label>`;
    }).join("");

    document.getElementById("leave-count").textContent = selectedCount;
    document.getElementById("affected-count").textContent = duties.length;
    document.getElementById("temp-assigned-count").textContent = assignedCount;
    document.getElementById("temp-pending-count").textContent = pendingCount;
    document.getElementById("temp-leave-badge").textContent = selectedCount === 1 ? "1 selected" : `${selectedCount} selected`;

    const percent = duties.length ? Math.round((assignedCount / duties.length) * 100) : 0;
    document.getElementById("temp-progress").innerHTML = `<div class="temp-progress-head"><span>Substitution progress ${assignedCount}/${duties.length}</span><span>${percent}%</span></div><div class="temp-progress-track"><div class="temp-progress-fill" style="width:${percent}%"></div></div>`;

    const cards = document.getElementById("temporary-cards");
    if (!selectedCount) {
        cards.innerHTML = `<div class="temp-empty"><div class="temp-empty-icon">🏖️</div><strong>No teacher selected</strong><div>Select teachers on leave from the left panel.</div></div>`;
    } else if (!duties.length) {
        cards.innerHTML = `<div class="temp-empty"><div class="temp-empty-icon">✅</div><strong>No affected periods</strong><div>The selected teachers have no permanent duties.</div></div>`;
    } else {
        cards.innerHTML = duties.map(duty => {
            const key = duty.absentTeacher + "__" + duty.period;
            const assignedTeacher = record.substitutions[key] || "None";
            const availableTeachers = appData.teachers.filter(teacher => isTeacherAvailable(teacher, duty.period, key) || teacher === assignedTeacher);
            return `<div class="card temporary-duty-card ${assignedTeacher !== "None" ? "is-assigned" : ""}">
                <div class="temp-duty-top"><span class="period-badge">Period ${duty.period + 1}</span><span class="temp-duty-status ${assignedTeacher !== "None" ? "done" : ""}">${assignedTeacher !== "None" ? "Assigned" : "Pending"}</span></div>
                <div class="temp-duty-class">${escapeHtml(duty.className)}</div><div class="temp-duty-subject">${escapeHtml(duty.subject === "None" ? "No subject" : duty.subject)}</div>
                <div class="absent-name">⚠️ Absent: ${escapeHtml(duty.absentTeacher)}</div>
                <label class="label">Available Substitute</label>
                <select onchange="assignSubstitute('${escapeJs(duty.absentTeacher)}', ${duty.period}, this.value)">
                    <option value="None">Select substitute</option>
                    ${availableTeachers.map(teacher => `<option value="${escapeHtml(teacher)}" ${assignedTeacher === teacher ? "selected" : ""}>${escapeHtml(teacher)}</option>`).join("")}
                </select>
            </div>`;
        }).join("");
    }

    renderTemporaryPrint(record, duties);
    initializeSmartSelects(cards);
    filterTemporaryTeachers();
}

function renderTemporaryPrint(record, duties) {
    const dateKey = document.getElementById("temporary-date").value;
    document.getElementById("temporary-print-school").textContent = appData.schoolName;
    document.getElementById("temporary-print-heading").textContent = appData.academicSession + " | Temporary Timetable: " + formatDate(dateKey);
    document.getElementById("temporary-print-leaves").textContent = "Teachers on Leave: " + (record.leaveTeachers.join(", ") || "None");

    const tableBody = document.getElementById("temporary-print-body");
    if (!duties.length) {
        tableBody.innerHTML = `<tr><td colspan="6">No affected duties.</td></tr>`;
        return;
    }

    tableBody.innerHTML = duties.map(duty => {
        const key = duty.absentTeacher + "__" + duty.period;
        const substitute = record.substitutions[key] || "Not Assigned";
        return `<tr>
            <td>Period ${duty.period + 1}</td>
            <td>${escapeHtml(duty.className)}</td>
            <td>${escapeHtml(duty.subject)}</td>
            <td>${escapeHtml(duty.absentTeacher)}</td>
            <td>${escapeHtml(substitute)}</td>
            <td>${substitute === "Not Assigned" ? "Pending" : "Assigned"}</td>
        </tr>`;
    }).join("");
}

function showPrintPreview(type) {
    const allowed = ["class", "teachers", "individual"];
    if (!allowed.includes(type)) type = "class";
    document.querySelectorAll(".print-preview-section").forEach(section => section.classList.remove("active"));
    document.querySelectorAll(".print-tab-btn").forEach(button => button.classList.remove("active"));
    document.getElementById("print-preview-" + type)?.classList.add("active");
    document.getElementById("print-tab-" + type)?.classList.add("active");
    const headings = {
        class: "Class Timetable",
        teachers: "Teacher Timetable",
        individual: "Individual Teacher Slips, 3 per page"
    };
    const title = document.getElementById("print-preview-title");
    if (title) title.textContent = headings[type];
}

function getShortDays(subject) {
    const days = Array.isArray(appData.subjectDays?.[subject]) ? appData.subjectDays[subject] : [];
    const x = { Monday: "M", Tuesday: "Tu", Wednesday: "W", Thursday: "Th", Friday: "F", Saturday: "Sa", Sunday: "Su" };
    return days.map(d => x[d] || d.slice(0, 2)).join(",");
}

function buildCompactClassCell(className, period, teachers) {
    if (!teachers.length) return "-";
    const uniqueTeachers = [...new Set(teachers)];
    const extra = appData.classExtraSubjects?.[className]?.[period] || "None";
    if (uniqueTeachers.length > 1) {
        const halves = uniqueTeachers.slice(0, 2).map((teacher, index) => {
            const primary = appData.teacherRoster?.[teacher]?.[period]?.subject || "None";
            const individualSubjects = [];
            if (primary !== "None") individualSubjects.push(primary);
            if (index === 1 && extra !== "None" && !individualSubjects.includes(extra)) individualSubjects.push(extra);
            const title = individualSubjects.length ? individualSubjects.map(subject => `<span class="${subject === extra ? "shared-extra" : ""}">${escapeHtml(subject)}</span>`).join(" / ") : "No subject";
            const dayText = individualSubjects.length > 1
                ? individualSubjects.map(subject => `<span class="cell-days-row"><strong>${escapeHtml(subject)}:</strong> ${escapeHtml(getShortDays(subject) || "-")}</span>`).join("")
                : escapeHtml(getShortDays(individualSubjects[0]) || "-");
            return `<div class="duty-half"><div class="duty-teacher">${escapeHtml(teacher)}</div><div class="duty-subject">${title}</div><div class="duty-days">${dayText}</div></div>`;
        }).join("");
        return `<div class="multi-duty-cell">${halves}</div>`;
    }
    const teacher = uniqueTeachers[0];
    const primary = appData.teacherRoster?.[teacher]?.[period]?.subject || "None";
    const subjects = [];
    if (primary !== "None") subjects.push(primary);
    if (extra !== "None" && !subjects.includes(extra)) subjects.push(extra);
    const titles = subjects.map(subject => `<span class="${subject === extra ? "cell-extra-subject" : ""}">${escapeHtml(subject)}</span>`).join(" / ");
    const rows = subjects.length > 1 ? subjects.map(subject => `<span class="cell-days-row"><strong>${escapeHtml(subject)}:</strong> ${escapeHtml(getShortDays(subject) || "-")}</span>`).join("") : subjects.length === 1 ? `<span class="cell-days-row">${escapeHtml(getShortDays(subjects[0]) || "-")}</span>` : "";
    return `<div class="cell-teachers">${escapeHtml(teacher)}</div><div class="cell-subjects">${titles}</div><div class="cell-days">${rows}</div>`;
}

function renderMasterPrint() {
    document.getElementById("master-print-school").textContent = appData.schoolName;
    document.getElementById("teacher-print-school").textContent = appData.schoolName;
    document.getElementById("master-print-session").textContent = appData.academicSession;
    document.getElementById("teacher-print-session").textContent = appData.academicSession;

    let classHeader = `<tr><th>Class Info</th>`;
    for (let period = 0; period < appData.totalPeriods; period++) {
        classHeader += `<th>Period ${period + 1}</th>`;
    }
    classHeader += "</tr>";
    document.getElementById("class-print-head").innerHTML = classHeader;

    document.getElementById("class-print-body").innerHTML = appData.classes.map(className => {
        let row = `<tr><td><strong>${escapeHtml(className)}</strong><br><small>Incharge: ${escapeHtml(appData.incharges[className] || "None")}</small></td>`;
        for (let period = 0; period < appData.totalPeriods; period++) {
            const teachers = appData.teachers.filter(t => appData.teacherRoster?.[t]?.[period]?.class === className);
            row += `<td>${buildCompactClassCell(className, period, teachers)}</td>`;
        }
        return row + "</tr>";
    }).join("");

    let teacherHeader = `<tr><th>Teacher</th>`;
    for (let period = 0; period < appData.totalPeriods; period++) {
        teacherHeader += `<th>Period ${period + 1}</th>`;
    }
    teacherHeader += "</tr>";
    document.getElementById("teacher-print-head").innerHTML = teacherHeader;

    document.getElementById("teacher-print-body").innerHTML = appData.teachers.map(teacher => {
        let row = `<tr><td><strong>${escapeHtml(teacher)}</strong></td>`;
        appData.teacherRoster[teacher].forEach((duty, period) => {
            if (duty.class === "None") {
                row += "<td>-</td>";
                return;
            }
            const extra = appData.classExtraSubjects?.[duty.class]?.[period] || "None";
            const subjects = extra !== "None" && extra !== duty.subject ? [duty.subject, extra] : [duty.subject];
            const title = subjects.map(z => `<span style="${z === extra ? 'font-weight:800;color:#4338ca' : ''}">${escapeHtml(z)}</span>`).join(" / ");
            const days = subjects.length > 1
                ? subjects.map(z => `<span class="print-subject-days ${z === extra ? 'print-extra-subject-days' : ''}"><strong>${escapeHtml(z)}:</strong> ${escapeHtml(getShortDays(z) || "-")}</span>`).join("")
                : `<span class="print-subject-days">${escapeHtml(getShortDays(subjects[0]) || "-")}</span>`;
            row += `<td><div class="teacher-table-class">${escapeHtml(duty.class)}</div><div class="teacher-table-subject"><strong>${title}</strong></div>${days}</td>`;
        });
        return row + "</tr>";
    }).join("");

    renderIndividualTeacherTimetables();

    const classTotal = document.getElementById("print-class-total");
    const teacherTotal = document.getElementById("print-teacher-total");
    const periodTotal = document.getElementById("print-period-total");
    const pageTotal = document.getElementById("print-page-total");
    if (classTotal) classTotal.textContent = appData.classes.length;
    if (teacherTotal) teacherTotal.textContent = appData.teachers.length;
    if (periodTotal) periodTotal.textContent = appData.totalPeriods;
    if (pageTotal) pageTotal.textContent = 2 + Math.ceil(appData.teachers.length / 3);
    requestAnimationFrame(() => applyAdaptiveTimetableFonts());
}

function renderIndividualTeacherTimetables() {
    const container = document.getElementById("individual-teacher-print-area");
    if (!container) return;

    const teacherGroups = [];
    for (let index = 0; index < appData.teachers.length; index += 3) {
        teacherGroups.push(appData.teachers.slice(index, index + 3));
    }

    container.innerHTML = teacherGroups.map(group => {
        const slips = group.map(teacher => {
            let headers = `<th>${escapeHtml(teacher)}</th>`;
            let dutyCells = `<td><span class="teacher-slip-label">Teacher Timetable</span></td>`;

            for (let period = 0; period < appData.totalPeriods; period++) {
                headers += `<th>Period ${period + 1}</th>`;
                const duty = appData.teacherRoster[teacher]?.[period] || { class: "None", subject: "None" };

                if (duty.class === "None") {
                    dutyCells += `<td class="teacher-slip-free">Free</td>`;
                } else {
                    const extra = appData.classExtraSubjects?.[duty.class]?.[period] || "None";
                    const subjects = extra !== "None" && extra !== duty.subject ? [duty.subject, extra] : [duty.subject];
                    const title = subjects.map(subject => subject === extra ? `<strong style="color:#4338ca">${escapeHtml(subject)}</strong>` : escapeHtml(subject)).join(" / ");
                    const days = subjects.length > 1
                        ? subjects.map(subject => `<span class="print-subject-days ${subject === extra ? "print-extra-subject-days" : ""}"><strong>${escapeHtml(subject)}:</strong> ${escapeHtml(getShortDays(subject) || "-")}</span>`).join("")
                        : `<span class="print-subject-days">${escapeHtml(getShortDays(subjects[0]) || "-")}</span>`;
                    dutyCells += `<td><span class="teacher-slip-class teacher-slip-class-top">${escapeHtml(duty.class)}</span><span class="teacher-slip-subject">${title}</span>${days}</td>`;
                }
            }

            return `
                <section class="teacher-slip-block">
                    <div class="teacher-slip-header">
                        <div class="teacher-slip-school">${escapeHtml(appData.schoolName)}</div>
                        <div class="teacher-slip-session">${escapeHtml(appData.academicSession)}</div>
                    </div>
                    <table class="print-table teacher-slip-table">
                        <thead><tr>${headers}</tr></thead>
                        <tbody><tr>${dutyCells}</tr></tbody>
                    </table>
                    <div class="teacher-slip-signatures">
                        <div class="teacher-slip-signature">Teacher Sign</div>
                        <div class="teacher-slip-signature">Incharge Sign</div>
                        <div class="teacher-slip-signature">Principal Sign</div>
                    </div>
                </section>
            `;
        }).join("");

        return `
            <div class="paper master-paper teacher-slip-page">
                ${slips}
            </div>
        `;
    }).join("");
}

function saveLocalData() {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(appData));
}

async function postToGoogleSheets(payload) {
    const response = await fetch(GOOGLE_SCRIPT_URL, {
        method: "POST",
        headers: { "Content-Type": "text/plain;charset=utf-8" },
        body: JSON.stringify(payload)
    });
    return response.json();
}

function saveMasterData() {
    saveLocalData();
    clearTimeout(cloudSaveTimer);
    cloudSaveTimer = setTimeout(async () => {
        if (masterLocked || GOOGLE_SCRIPT_URL.startsWith("PASTE_")) return;
        try {
            const result = await postToGoogleSheets({ action: "saveData", data: appData });
            if (result.masterLocked) {
                masterLocked = true;
                applyMasterLockUI();
            }
        } catch (error) {
            console.error("Master saving failed.", error);
        }
    }, 350);
}

function saveTemporaryData() {
    saveLocalData();
    clearTimeout(cloudSaveTimer);
    cloudSaveTimer = setTimeout(async () => {
        if (GOOGLE_SCRIPT_URL.startsWith("PASTE_")) return;
        const dateKey = document.getElementById("temporary-date").value || getLocalDateKey();
        try {
            const result = await postToGoogleSheets({
                action: "saveTemporary",
                dateKey: dateKey,
                temporaryRecord: appData.temporaryTimetables[dateKey]
            });
            if (typeof result.masterLocked === "boolean") {
                masterLocked = result.masterLocked;
                applyMasterLockUI();
            }
        } catch (error) {
            console.error("Temporary saving failed.", error);
        }
    }, 350);
}

function openReliablePrintWindow(title, selector) {
    if (typeof closeAllSmartSelects === "function") closeAllSmartSelects();
    applyAdaptiveTimetableFonts();

    const papers = Array.from(document.querySelectorAll(selector));
    if (!papers.length) {
        alert("Print content not found. Please open the Print page.");
        return;
    }

    const pageContent = papers.map(paper => paper.outerHTML).join("\n");
    const fullHtml = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<link rel="stylesheet" href="style.css">
<style>
@page { size: A4 landscape; margin: 3mm !important; }
body { background: white !important; padding: 0 !important; color: #111 !important; }
.paper { display: block !important; width: 100% !important; min-height: 185mm !important; margin: 0 !important; padding: 3mm !important; box-shadow: none !important; border: none !important; page-break-after: always !important; }
.paper:last-child { page-break-after: auto !important; }
.print-table { display: table !important; width: 100% !important; border-collapse: collapse !important; margin-top: 12px !important; }
.print-table th, .print-table td { border: 1px solid #777 !important; padding: 3px !important; text-align: center !important; }
.print-table th { background: #e5e7eb !important; }
.signatures, .teacher-slip-signatures { display: flex !important; justify-content: space-around !important; margin-top: 35px !important; }
.signature, .teacher-slip-signature { width: 150px !important; border-top: 1px solid #111 !important; padding-top: 5px !important; text-align: center !important; font-size: 8pt !important; }
</style>
</head>
<body>${pageContent}</body>
</html>`;

    // Check for native Android Bridge first!
    if (window.AndroidBridge && typeof window.AndroidBridge.printHtml === "function") {
        window.AndroidBridge.printHtml(title, fullHtml);
        return;
    }

    // Fallback to standard window.print() or popup window
    const printWindow = window.open("", "_blank");
    if (!printWindow) {
        window.print();
        return;
    }

    printWindow.document.open();
    printWindow.document.write(fullHtml);
    printWindow.document.close();

    const startPrint = () => {
        printWindow.focus();
        setTimeout(() => printWindow.print(), 600);
    };

    if (printWindow.document.readyState === "complete") startPrint();
    else printWindow.onload = startPrint;
}

function printMasterTimetables() {
    renderMasterPrint();
    openReliablePrintWindow("Class and Teacher Timetables", "#page3 .master-paper");
}

function printTemporaryTimetable() {
    renderTemporaryTimetable();
    openReliablePrintWindow("Temporary Timetable", "#page4 .temporary-paper");
}

const smartSelectArrow = `
    <svg class="smart-select-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polyline points="6 9 12 15 18 9"></polyline>
    </svg>
`;

function closeAllSmartSelects(exceptWrapper = null) {
    document.querySelectorAll(".smart-select").forEach(wrapper => {
        if (exceptWrapper && wrapper === exceptWrapper) return;
        wrapper.querySelector(".smart-select-button")?.classList.remove("open");
        wrapper.querySelector(".smart-select-list")?.classList.remove("open");
        wrapper.closest(".card, .period-card")?.classList.remove("smart-dropdown-active");
    });
}

function createSmartSelect(selectElement) {
    if (selectElement.dataset.smartSelect === "true") {
        updateSmartSelect(selectElement);
        return;
    }

    selectElement.dataset.smartSelect = "true";
    const wrapper = document.createElement("div");
    wrapper.className = "smart-select";
    selectElement.parentNode.insertBefore(wrapper, selectElement);
    wrapper.appendChild(selectElement);

    const displayButton = document.createElement("button");
    displayButton.type = "button";
    displayButton.className = "smart-select-button";

    const selectedText = document.createElement("span");
    selectedText.className = "smart-select-text";
    displayButton.appendChild(selectedText);
    displayButton.insertAdjacentHTML("beforeend", smartSelectArrow);

    const optionsList = document.createElement("div");
    optionsList.className = "smart-select-list";

    wrapper.appendChild(displayButton);
    wrapper.appendChild(optionsList);

    displayButton.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        if (selectElement.disabled) return;

        const isAlreadyOpen = optionsList.classList.contains("open");
        closeAllSmartSelects(isAlreadyOpen ? null : wrapper);

        if (isAlreadyOpen) {
            displayButton.classList.remove("open");
            optionsList.classList.remove("open");
            wrapper.closest(".card, .period-card")?.classList.remove("smart-dropdown-active");
            return;
        }

        displayButton.classList.add("open");
        optionsList.classList.add("open");
        wrapper.closest(".card, .period-card")?.classList.add("smart-dropdown-active");

        const selectedOption = optionsList.querySelector(".smart-select-option.selected");
        if (selectedOption) selectedOption.scrollIntoView({ block: "nearest" });
    });

    selectElement.addEventListener("change", () => {
        updateSmartSelect(selectElement);
    });

    buildSmartSelectOptions(selectElement);
}

function buildSmartSelectOptions(selectElement) {
    const wrapper = selectElement.closest(".smart-select");
    if (!wrapper) return;
    const optionsList = wrapper.querySelector(".smart-select-list");
    if (!optionsList) return;
    optionsList.innerHTML = "";

    Array.from(selectElement.options).forEach((nativeOption, index) => {
        const optionButton = document.createElement("button");
        optionButton.type = "button";
        optionButton.className = "smart-select-option";
        optionButton.textContent = nativeOption.textContent;
        optionButton.dataset.value = nativeOption.value;

        if (nativeOption.selected) optionButton.classList.add("selected");
        if (nativeOption.disabled) optionButton.disabled = true;

        optionButton.addEventListener("click", event => {
            event.preventDefault();
            event.stopPropagation();
            if (nativeOption.disabled || selectElement.disabled) return;

            selectElement.selectedIndex = index;
            selectElement.value = nativeOption.value;
            selectElement.dispatchEvent(new Event("change", { bubbles: true }));
            closeAllSmartSelects();
        });

        optionsList.appendChild(optionButton);
    });

    updateSmartSelect(selectElement);
}

function updateSmartSelect(selectElement) {
    const wrapper = selectElement.closest(".smart-select");
    if (!wrapper) return;
    const displayButton = wrapper.querySelector(".smart-select-button");
    const selectedText = wrapper.querySelector(".smart-select-text");
    const optionsList = wrapper.querySelector(".smart-select-list");
    if (!displayButton || !selectedText || !optionsList) return;

    const selectedOption = selectElement.options[selectElement.selectedIndex];
    selectedText.textContent = selectedOption ? selectedOption.textContent : "Select";

    displayButton.classList.toggle("disabled", selectElement.disabled);
    displayButton.disabled = selectElement.disabled;

    Array.from(optionsList.children).forEach((optionButton, index) => {
        optionButton.classList.toggle("selected", index === selectElement.selectedIndex);
    });
}

function initializeSmartSelects(container = document) {
    container.querySelectorAll("select:not([data-smart-select='true'])").forEach(selectElement => {
        createSmartSelect(selectElement);
    });
    container.querySelectorAll("select[data-smart-select='true']").forEach(selectElement => {
        updateSmartSelect(selectElement);
    });
}

document.addEventListener("click", () => closeAllSmartSelects());
document.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        closeAllSmartSelects();
        closeMobileMenu();
    }
});

const smartSelectObserver = new MutationObserver(mutations => {
    let newSelectFound = false;
    mutations.forEach(mutation => {
        mutation.addedNodes.forEach(addedNode => {
            if (addedNode.nodeType !== Node.ELEMENT_NODE) return;
            if (addedNode.matches && addedNode.matches("select")) newSelectFound = true;
            if (addedNode.querySelector && addedNode.querySelector("select")) newSelectFound = true;
        });
    });
    if (newSelectFound) {
        requestAnimationFrame(() => initializeSmartSelects());
    }
});

window.addEventListener("load", () => {
    initializeSmartSelects();
    smartSelectObserver.observe(document.body, { childList: true, subtree: true });
});

function applyAdaptiveTimetableFonts(root = document) {
    const cells = root.querySelectorAll("#class-print-body td, #teacher-print-body td");
    cells.forEach(cell => {
        cell.classList.remove("fit-roomy", "fit-normal", "fit-compact", "fit-tight");
        if (cell.cellIndex === 0) return;
        const plainText = (cell.innerText || cell.textContent || "").replace(/\s+/g, " ").trim();
        const lineBlocks = cell.querySelectorAll(".cell-days-row, .print-subject-days, .duty-half").length;
        const isSplit = Boolean(cell.querySelector(".multi-duty-cell"));
        let score = plainText.length + lineBlocks * 12 + (isSplit ? 36 : 0);
        let fitClass = "fit-normal";
        if (score <= 28) fitClass = "fit-roomy";
        else if (score <= 62) fitClass = "fit-normal";
        else if (score <= 100) fitClass = "fit-compact";
        else fitClass = "fit-tight";
        cell.classList.add(fitClass);
    });
}
