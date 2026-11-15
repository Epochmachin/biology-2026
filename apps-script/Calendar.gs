/**
 * জীববিজ্ঞান স্টাডি সেন্টার → Google Calendar সংযোগ
 * অ্যাপে নোটিশ (পরীক্ষা, নোট, এক্সট্রা ক্লাস…) সেভ করলে এখানে আসে,
 * আর এটা আপনার নিজের Google Calendar-এ ইভেন্ট বানায়/বদলায়/মোছে।
 *
 * নিরাপত্তা: লিংকের শেষে ?k=গোপন-কোড থাকে। কোড না মিললে কিছুই হয় না।
 * কোড বদলাতে চাইলে নিচের KEY বদলে আবার Deploy করুন, অ্যাপের সেটিংসেও নতুন লিংক দিন।
 */
const KEY = '__KEY__';
const CAL_ID = 'primary';          // অন্য ক্যালেন্ডারে চাইলে তার ID দিন
const TZ = 'Asia/Dhaka';

function doGet(e) { return handle_(e, {action: 'ping'}); }

function doPost(e) {
  let body = {};
  try { body = JSON.parse(e.postData.contents); } catch (err) { return out_({ok: false, error: 'ভুল ডেটা'}); }
  return handle_(e, body);
}

function handle_(e, body) {
  if (!e || !e.parameter || e.parameter.k !== KEY) return out_({ok: false, error: 'গোপন কোড মেলেনি'});
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const cal = CAL_ID === 'primary' ? CalendarApp.getDefaultCalendar() : CalendarApp.getCalendarById(CAL_ID);
    if (body.action === 'ping') return out_({ok: true, calendar: cal.getName()});
    if (!body.id || !/^[\w-]+$/.test(body.id)) return out_({ok: false, error: 'নোটিশের id নেই'});
    removeOld_(cal, body.id);
    if (body.action === 'delete') return out_({ok: true});
    if (body.action !== 'upsert' || !Array.isArray(body.items)) return out_({ok: false, error: 'অজানা কাজ'});
    const ids = body.items.slice(0, 5).map(it => makeEvent_(cal, it).getId());
    PropertiesService.getScriptProperties().setProperty('ev_' + body.id, JSON.stringify(ids));
    return out_({ok: true, n: ids.length});
  } catch (err) {
    return out_({ok: false, error: String(err && err.message || err).slice(0, 120)});
  } finally {
    lock.releaseLock();
  }
}

function removeOld_(cal, id) {
  const props = PropertiesService.getScriptProperties(), k = 'ev_' + id;
  const old = JSON.parse(props.getProperty(k) || '[]');
  old.forEach(eid => { try { const ev = cal.getEventById(eid); if (ev) ev.deleteEvent(); } catch (x) {} });
  props.deleteProperty(k);
}

function parse_(s) {   // "2026-11-16T16:00:00" → ঢাকার সময় ধরে Date
  return Utilities.parseDate(s, TZ, "yyyy-MM-dd'T'HH:mm:ss");
}

function makeEvent_(cal, it) {
  const title = String(it.title || 'কোচিং').slice(0, 200), opt = {description: String(it.desc || '').slice(0, 6000)};
  let ev;
  if (it.allDay) ev = cal.createAllDayEvent(title, Utilities.parseDate(it.date, TZ, 'yyyy-MM-dd'), opt);
  else ev = cal.createEvent(title, parse_(it.start), parse_(it.end), opt);
  try { if (it.color) ev.setColor(String(it.color)); } catch (x) {}
  ev.removeAllReminders();
  (it.popups || []).slice(0, 5).forEach(m => ev.addPopupReminder(Math.max(0, Math.min(40320, Number(m) || 0))));
  return ev;
}

function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/** প্রথমবার Run চাপলে Calendar-এর অনুমতি চাইবে, আর একটা পরীক্ষামূলক কাজ করে দেখাবে */
function testSetup() {
  const cal = CalendarApp.getDefaultCalendar();
  Logger.log('Calendar ঠিক আছে: ' + cal.getName());
}
