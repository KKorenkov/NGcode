// Исходящий звонок из Android-приложения -> на обычный телефон (PSTN)
// с купленного номера в качестве номера звонящего (Caller ID).
//
// Правило маршрутизации: шаблон .* (или, например, \+?[0-9]+), сценарий — этот.
// Правило должно стоять НИЖЕ правила для входящих (inbound.js).

const CALLER_ID = "74951234567"; // ваш купленный номер Voximplant, только цифры

VoxEngine.addEventListener(AppEvents.CallAlerting, (e) => {
  const fromApp = e.call;
  const number = e.destination.replace(/[^\d]/g, ""); // номер, набранный в приложении

  const toPhone = VoxEngine.callPSTN(number, CALLER_ID);

  // Гудки у вызываемого абонента -> в приложении статус "Гудки…"
  toPhone.addEventListener(CallEvents.Ringing, () => fromApp.ring());

  toPhone.addEventListener(CallEvents.Connected, () => {
    fromApp.answer();
    VoxEngine.sendMediaBetween(fromApp, toPhone);
  });

  toPhone.addEventListener(CallEvents.Failed, () => {
    fromApp.hangup();
    VoxEngine.terminate();
  });

  toPhone.addEventListener(CallEvents.Disconnected, () => {
    fromApp.hangup();
    VoxEngine.terminate();
  });

  fromApp.addEventListener(CallEvents.Disconnected, () => {
    toPhone.hangup();
    VoxEngine.terminate();
  });
});
