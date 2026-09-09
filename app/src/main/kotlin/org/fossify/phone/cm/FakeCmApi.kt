package org.fossify.phone.cm

/**
 * In-app stand-in for `cm-api` until the real service exists. Active whenever the
 * CM base URL in Settings is blank or "mock". Keeps created contacts in memory so
 * "save from call history" is visible end to end.
 */
class FakeCmApi : CmApi {

    private val contacts = sampleContacts().toMutableList()
    private var counter = contacts.size

    override fun pair(code: String): PairResponse {
        if (code.trim().length !in VALID_CODE_LENGTH) {
            throw CmApiException(statusCode = 401, message = "Invalid pairing code")
        }

        return PairResponse(
            accessToken = "fake-access-token",
            refreshToken = "fake-refresh-token",
            expiresInSec = 3600,
            user = CmUser(id = "u-fake", name = "Konto testowe CM"),
        )
    }

    override fun refresh(refreshToken: String): RefreshResponse {
        return RefreshResponse(
            accessToken = "fake-access-token-refreshed",
            refreshToken = "fake-refresh-token",
            expiresInSec = 3600,
        )
    }

    override fun getContacts(sinceMillis: Long?): List<CmContact> {
        return contacts.toList()
    }

    override fun createContact(request: CreateContactRequest): CmContact {
        counter++
        val created = CmContact(
            id = "c-fake-$counter",
            firstName = request.firstName,
            lastName = request.lastName,
            phoneNumbers = request.phoneNumbers,
            note = request.note,
            updatedAt = System.currentTimeMillis(),
        )
        contacts.add(created)
        return created
    }

    companion object {
        private val VALID_CODE_LENGTH = 4..8

        private fun sampleContacts(): List<CmContact> = listOf(
            CmContact(
                id = "c1",
                firstName = "Anna",
                lastName = "Nowak",
                phoneNumbers = listOf(CmPhoneNumber("+48600100200", "mobile")),
                note = "Zainteresowana mieszkaniem 2-pokojowym do 600 tys. Oddzwonić w piątek.",
                relatedProperty = CmRef("p9", "ul. Długa 4/12, Gdańsk"),
                relatedClient = CmRef("cl3", "Biuro Nieruchomości XYZ"),
                updatedAt = 1_725_800_000_000,
            ),
            CmContact(
                id = "c2",
                firstName = "Piotr",
                lastName = "Kowalczyk",
                phoneNumbers = listOf(CmPhoneNumber("+48512345678", "mobile")),
                note = "Sprzedający, dom w Sopocie. Umówiona wycena na przyszły tydzień.",
                relatedProperty = CmRef("p14", "ul. Morska 8, Sopot"),
                updatedAt = 1_725_810_000_000,
            ),
            CmContact(
                id = "c3",
                firstName = "Katarzyna",
                lastName = "Wiśniewska",
                phoneNumbers = listOf(
                    CmPhoneNumber("+48734000111", "mobile"),
                    CmPhoneNumber("+48583030303", "work"),
                ),
                relatedClient = CmRef("cl7", "Deweloper Bałtyk Sp. z o.o."),
                updatedAt = 1_725_820_000_000,
            ),
            CmContact(
                id = "c4",
                firstName = "Tomasz",
                lastName = "Lewandowski",
                phoneNumbers = listOf(CmPhoneNumber("+48605998877", "mobile")),
                note = "Inwestor, szuka lokali pod wynajem krótkoterminowy.",
                updatedAt = 1_725_830_000_000,
            ),
            CmContact(
                id = "c5",
                firstName = "Magdalena",
                lastName = "Dąbrowska",
                phoneNumbers = listOf(CmPhoneNumber("+48501202303", "mobile")),
                relatedProperty = CmRef("p21", "ul. Grunwaldzka 120/5, Gdańsk"),
                relatedClient = CmRef("cl3", "Biuro Nieruchomości XYZ"),
                updatedAt = 1_725_840_000_000,
            ),
            CmContact(
                id = "c6",
                firstName = "Marek",
                lastName = "Zieliński",
                phoneNumbers = listOf(CmPhoneNumber("+48889112233", "mobile")),
                note = "Klient po przetargu, czeka na decyzję kredytową.",
                updatedAt = 1_725_850_000_000,
            ),
        )
    }
}
