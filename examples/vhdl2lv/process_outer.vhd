library ieee;
use ieee.std_logic_1164.all;

entity process_outer is
  port(
    clk   : in  std_logic;
    in1   : in  std_logic;
    in2   : in  std_logic;
    out1  : out std_logic;
    out2  : out std_logic;
    out3  : out std_logic
    );
end entity;

architecture behavioral of process_outer is
begin
  process (clk, rst) is
  begin
    if clk'event and clk = '1' then
      if in1 = '1' then
        -- Missing else branch.
        out1 <= in2;
      end if;
      if in1 = '1' then
        out2 <= in2;
      else
        -- Assigned in every branch.
        out2 <= not in2;
        -- Assigned in one branch (out of two possible).
        out3 <= in2;
      end if;
      -- Assignment in every branch, but depends on previous value.
      out4 <= not out4;
      -- Assignment in every branch and combination of other signals.
      out5 <= out4 and in2;
    end if;
  end process;
  rst <= '1';
end behavioral;
