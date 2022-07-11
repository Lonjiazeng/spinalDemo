package Pipe_line

import spinal.core._

class PipeMmap extends Component {
    /**
     *  处理CPU指令读写请求
     */
    val io_inst = new Bundle {
        val inst_pc = in Bits(32 bits)
        val inst_re = in Bool()
        val inst_ready = out Bool()
        val inst_data = out Bits(32 bits)
    }

    /**
     * 处理CPU数据读写请求
     */
    val io_data = new Bundle {
          val data_data_in = in Bits(32 bits)
          val data_addr = in Bits(32 bits)
          val data_re = in Bool()
          val data_we = in Bool()
          val data_byte_en = in Bits(4 bits)
    }
    /**
     * 访存的mem阶段
     */
    val io_mem = new Bundle {
          val data_data_out = out Bits(32 bits)
          val data_data_ready = out Bool()
    }

    /**
     * 处理base_ram读写,处理ext_ram读写
     */
    val io_ram = new Bundle {
        val base_ram_data = inout (Analog(Bits(32 bits)))
        val base_ram_addr = out Bits(20 bits)
        val base_ram_be_n = out Bits(4 bits)
        val base_ram_ce_n = out Bool()
        val base_ram_re_n = out Bool()
        val base_ram_we_n = out Bool()


        val ext_ram_data = inout (Analog(Bits(32 bits)))
        val ext_ram_addr = out Bits(20 bits)
        val ext_ram_be_n = out Bits(4 bits)
        val ext_ram_ce_n = out Bool()
        val ext_ram_re_n = out Bool()
        val ext_ram_we_n = out Bool()

        val serial_rdata_en_sign = out Bool()
        val serial_rdata_en_data = out Bool()
        val serial_rdata = in Bits(8 bits)
        val serial_wdata = out Bits(8 bits)
        val serial_wdata_en = out Bool()
    }
    val data_data_reg = Reg(Bits(32 bits)) init(0)
    io_mem.data_data_out := data_data_reg

    // 串口
    val data_to_serial_req_sign = io_data.data_re && (io_data.data_addr === B"32'hbfd003fc")
    val data_to_serial_req_data_r = io_data.data_re && (io_data.data_addr === B"32'hbfd003f8")
    val data_to_serial_req_data_w = io_data.data_we && (io_data.data_addr === B"32'hbfd003f8")
    val data_to_serial_req_data = data_to_serial_req_data_r || data_to_serial_req_data_w
    val data_to_serial_req =  data_to_serial_req_sign || data_to_serial_req_data
    io_ram.serial_rdata_en_sign := data_to_serial_req_sign
    io_ram.serial_rdata_en_data := data_to_serial_req_data_r
    io_ram.serial_wdata_en := data_to_serial_req_data_w
    val serial_r_data = io_ram.serial_rdata.asSInt.resize(32 bits).asBits
    io_ram.serial_wdata := io_data.data_data_in(7 downto 0)

    // 处理inst请求
    val req_inst = io_inst.inst_re
    val ram_en_inst = io_inst.inst_pc(22)

    // 处理data请求
    val req_data = (io_data.data_re || io_data.data_we) && !data_to_serial_req
    val ram_en_data = io_data.data_addr(22)

    // 处理交互请求
    io_inst.inst_ready := True
    io_mem.data_data_ready := True
        // data 优先
    when(req_inst && req_data && (ram_en_inst === ram_en_data)) {
        io_inst.inst_ready := False
    }

    // 处理下层内存请求
    when(io_data.data_we) {
        when(ram_en_data) {
            io_ram.ext_ram_data := io_data.data_data_in
        }otherwise {
            io_ram.base_ram_data := io_data.data_data_in
        }
    }
    io_ram.base_ram_addr := (req_data && !ram_en_data) ? io_data.data_addr(21 downto 2).asBits | io_inst.inst_pc(21 downto 2).asBits
    io_ram.base_ram_be_n := (req_data && !ram_en_data) ? ~io_data.data_byte_en | B"4'h0"
    io_ram.base_ram_ce_n := !req_inst && !req_data || !req_inst && ram_en_data || ram_en_data && ram_en_inst || !req_data && ram_en_inst
    io_ram.base_ram_re_n := !(req_inst || req_data)
    io_ram.base_ram_we_n := ~io_data.data_we || ram_en_data


    io_ram.ext_ram_addr :=  (req_data && ram_en_data) ? io_data.data_addr(21 downto 2).asBits | io_inst.inst_pc(21 downto 2).asBits
    io_ram.ext_ram_be_n :=  (req_data && ram_en_data) ? ~io_data.data_byte_en | B"4'h0"
    io_ram.ext_ram_ce_n :=  !req_inst && !req_data || !req_inst && !ram_en_data || !ram_en_inst && !ram_en_data || !req_data && !ram_en_inst
    io_ram.ext_ram_re_n := !(req_inst || req_data)
    io_ram.ext_ram_we_n := ~io_data.data_we || ~ram_en_data

    io_inst.inst_data := ram_en_inst ? io_ram.ext_ram_data | io_ram.base_ram_data
    data_data_reg := (data_to_serial_req_sign || data_to_serial_req_data_r) ? serial_r_data | (ram_en_data ? io_ram.ext_ram_data | io_ram.base_ram_data)
}