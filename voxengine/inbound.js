// Входящий звонок на купленный номер -> пользователю приложения (в Android-приложение).
//
// Правило маршрутизации: привяжите купленный номер к приложению и создайте правило
// с шаблоном, совпадающим с этим номером (например 74951234567), указав этот сценарий.

const USERNAME = "user1"; // имя пользователя Voximplant без "@app.account..."

VoxEngine.addEventListener(AppEvents.CallAlerting, (e) => {
  const toApp = VoxEngine.callUser({
    username: USERNAME,
    callerid: e.callerid,
    displayName: e.callerid, // в приложении будет показан номер звонящего
  });
  // Соединяет звонки, пробрасывает ответ/завершение и закрывает сессию.
  VoxEngine.easyProcess(e.call, toApp);
});
