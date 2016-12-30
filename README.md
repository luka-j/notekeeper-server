# Notekeeper - server
(previously called StudyGroup and StudyBuddy)

## Ideja
Čuvati sve što je neophodno za prikazivanje beležaka, pitanja, kontrolnih, itd. na serveru, jednog dana napraviti i web platformu koja će prikazivati sve isto kao na Androidu.

## Platform
Play 2.5, Ebean ORM, PostgreSQL
Pisano u IDEA-i

## Hijerarhija projekta
* app - Java sorsovi i Scala HTML templejtovi (više HTMLa nego Skale) za aplikaciju
    * controllers - izvršavanje operacija, metode koje se pozivaju nakon HTTP zahteva i Restrict enum za access restriction i logovanje
    * models - svaka klasa predstavlja tabelu u bazi (sem EditableItem interfejsa čija je suština forsiranje istih metoda za editovanje stvari i static utility metoda za format čuvanja toga u bazi, jer Ebean ne podržava @ElementCollection)
    * mails - TODO, mailing invites & verifying email
    * views - .scala.html fajlovi, prikaz u pretraživaču, nebitno za aplikaciju, iz dana kada sam bio optimističan i nadao se da ću i to stići
* conf - konfiguracija servera i evolucije baze
    * evolutions.default - jednog dana mesto za čuvanje evolucija, u debug modu brišem celu bazu i rekreiram je kad menjam tabele
    * messages/messages.sr - poruke i prevodi za sajt (views)
    * application.conf - podešavanje konekcije za bazu, crypto secret za debug (u prod bi trebalo da se prosleđuje preko argumenta u shellu, da ne čuvam u fajlu)
    * routes - rute, mapiranje http zahteva u metode iz kontrolera
    
## TODO
* Mejlovi
* testing/bugfixing
* limit group size (members+invitations) to something sensible (4k?)
* ETag/last-modified caching
* static assets caching (Assets.at)
* //todo komentari u kodu
* dokumentacija