package com.sentinel.quantum.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDirectoryClientTest {
    @Test
    fun arcep_normalizes_french_numbers_and_short_codes() {
        assertEquals("0612345678", ArcepDirectoryClient.toFrenchNational("+33 6 12 34 56 78"))
        assertEquals("0612345678", ArcepDirectoryClient.toFrenchNational("0033 6 12 34 56 78"))
        assertEquals("3018", ArcepDirectoryClient.toFrenchNational("3018"))
        assertNull(ArcepDirectoryClient.toFrenchNational("+43 1 234567"))
    }

    @Test
    fun arcep_finds_matching_allocation_and_rejects_wrong_schema() {
        val directory = JSONObject("""{
          "schemaVersion":2,
          "operators":{"OP1":["Example Telecom","123456789","RCS TEST","1 rue Test",true,"2026-01-01"]},
          "entries":[["0612000000","0612999999","OP1","FR","2026-02-03"]]
        }""")
        val result = ArcepDirectoryClient.find(directory, "0612345678")
        requireNotNull(result)
        assertEquals("Example Telecom", result.attributedOperator)
        assertEquals("0612000000", result.start)
        assertEquals("0612999999", result.end)
        assertEquals("123456789", result.businessIdentifier)
        assertTrue(result.canReceiveNumbering == true)

        assertThrows(IllegalStateException::class.java) {
            ArcepDirectoryClient.find(JSONObject("""{"schemaVersion":1,"entries":[]}"""), "0612345678")
        }
    }

    @Test
    fun rtr_normalizes_austrian_numbers() {
        assertEquals("+431234567", RtrDirectoryClient.normalize("+43 1 234567"))
        assertEquals("+431234567", RtrDirectoryClient.normalize("0043 1 234567"))
        assertNull(RtrDirectoryClient.normalize("+33 6 12 34 56 78"))
    }

    @Test
    fun rtr_returns_allocated_and_ambiguous_without_silently_picking_first() {
        val allocated = JSONObject("""{
          "schemaVersion":1,"country":"AT",
          "holders":[["Holder A","RTR-A"]],
          "groups":{"1/6":{"category":"geo","area":"Wien","ranges":[["200000","299999",0]]}}
        }""")
        val one = RtrDirectoryClient.find(allocated, "+431234567")
        assertEquals("allocated", one.status)
        assertEquals(1, one.matches.size)
        assertEquals("Holder A", one.matches.single().allocationHolder)

        val ambiguous = JSONObject("""{
          "schemaVersion":1,"country":"AT",
          "holders":[["Holder A","RTR-A"],["Holder B","RTR-B"]],
          "groups":{
            "1/6":{"category":"geo","area":"Wien","ranges":[["200000","299999",0]]},
            "12/5":{"category":"service","area":"","ranges":[["30000","39999",1]]}
          }
        }""")
        val many = RtrDirectoryClient.find(ambiguous, "+431234567")
        assertEquals("ambiguous", many.status)
        assertEquals(2, many.matches.size)
    }

    @Test
    fun rtr_maps_negative_holder_index_to_explicit_status() {
        val directory = JSONObject("""{
          "schemaVersion":1,"country":"AT","holders":[],
          "groups":{"1/6":{"category":"geo","area":"Wien","ranges":[["200000","299999",-1]]}}
        }""")
        val result = RtrDirectoryClient.find(directory, "+431234567")
        assertEquals("unallocated", result.status)
        assertEquals("unallocated", result.matches.single().status)
    }
}
